package com.finvista.service;

import com.finvista.dto.DashboardAnalysisResponse;
import com.finvista.dto.DashboardAnalysisResponse.*;
import com.finvista.dto.DashboardResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardAnalysisService {

    private final FinancialTransactionRepository repository;
    private final ClienteContextService contexto;
    private final FinancialReferenceService referencia;
    private final ExpenseAllocationService rateios;

    public DashboardAnalysisService(FinancialTransactionRepository repository, ClienteContextService contexto,
            FinancialReferenceService referencia, ExpenseAllocationService rateios) {
        this.repository = repository;
        this.contexto = contexto;
        this.referencia = referencia;
        this.rateios = rateios;
    }

    @Transactional(readOnly = true)
    public DashboardAnalysisResponse consultar(LocalDate inicio, LocalDate fim, String categoria) {
        YearMonth ref = referencia.obterMesReferencia();
        if ((inicio == null) != (fim == null)) {
            throw new IllegalArgumentException("Informe as duas datas.");
        }
        if (inicio == null) {
            inicio = ref.atDay(1);
            fim = ref.atEndOfMonth();
        }
        if (fim.isBefore(inicio) || ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(fim)) > 23) {
            throw new IllegalArgumentException("Escolha um intervalo válido de até 24 meses de calendário.");
        }
        String filtro = categoria == null || categoria.isBlank() ? null : categoria.trim();
        if (filtro != null && filtro.length() > 255) {
            throw new IllegalArgumentException("Categoria inválida.");
        }
        LocalDate inicioAnterior, fimAnterior;
        if (inicio.getDayOfMonth() == 1 && fim.equals(YearMonth.from(fim).atEndOfMonth())) {
            long meses = ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(fim)) + 1;
            inicioAnterior = inicio.minusMonths(meses);
            fimAnterior = inicio.minusDays(1);
        } else {
            long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
            fimAnterior = inicio.minusDays(1);
            inicioAnterior = inicio.minusDays(dias);
        }
        Long clienteId = contexto.getClienteAtualId();
        LocalDate primeiro = inicio, ultimo = fim, limiteAnterior = inicioAnterior;
        List<FinancialTransaction> registros = repository.findByClienteIdAndDataBetweenOrderByDataAsc(clienteId, inicioAnterior, fim)
                .stream().filter(t -> t.getData() != null && !t.getData().isBefore(limiteAnterior) && !t.getData().isAfter(ultimo)
                        && t.getValor() != null && ("RECEITA".equals(tipo(t)) || "DESPESA".equals(tipo(t))))
                .sorted(Comparator.comparing(FinancialTransaction::getData).thenComparing(FinancialTransaction::getId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        Map<FinancialTransaction, List<ExpenseAllocationService.Parte>> partes = rateios.dividirDetalhado(clienteId, registros).stream()
                .collect(Collectors.groupingBy(ExpenseAllocationService.ParteDetalhada::lancamento, LinkedHashMap::new,
                        Collectors.mapping(ExpenseAllocationService.ParteDetalhada::parte, Collectors.toList())));
        SortedSet<String> categorias = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        partes.values().forEach(list -> list.forEach(p -> categorias.add(p.categoria())));
        if (filtro != null) {
            categorias.add(filtro);
        }
        BigDecimal receita = BigDecimal.ZERO, despesa = BigDecimal.ZERO, receitaAnterior = BigDecimal.ZERO, despesaAnterior = BigDecimal.ZERO;
        Map<YearMonth, BigDecimal[]> mensal = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fim)); m = m.plusMonths(1)) {
            mensal.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        Map<String, BigDecimal[]> variacoes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Item> itens = new ArrayList<>();
        for (FinancialTransaction t : registros) {
            List<ExpenseAllocationService.Parte> selecionadas = partes.getOrDefault(t, List.of()).stream()
                    .filter(p -> filtro == null || p.categoria().equalsIgnoreCase(filtro)).toList();
            if (selecionadas.isEmpty()) {
                continue;
            }
            BigDecimal valor = selecionadas.stream().map(ExpenseAllocationService.Parte::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean atual = !t.getData().isBefore(primeiro);
            boolean income = "RECEITA".equals(tipo(t));
            if (atual) {
                if (income) {
                    receita = receita.add(valor);
                } else {
                    despesa = despesa.add(valor);
                }
                mensal.get(YearMonth.from(t.getData()))[income ? 0 : 1] = mensal.get(YearMonth.from(t.getData()))[income ? 0 : 1].add(valor);
                itens.add(new Item(t.getId(), t.getData(), t.getDescricao(), tipo(t), valor, t.getValor(), selecionadas.stream().map(ExpenseAllocationService.Parte::categoria).distinct().toList()));
            } else {
                if (income) {
                    receitaAnterior = receitaAnterior.add(valor);
                } else {
                    despesaAnterior = despesaAnterior.add(valor);

                }}
            if (!income) {
                for (var parte : selecionadas) {
                    BigDecimal[] par = variacoes.computeIfAbsent(parte.categoria(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    int i = atual ? 1 : 0;
                    par[i] = par[i].add(parte.valor());
                }
            }
        }
        BigDecimal resultado = receita.subtract(despesa), anterior = receitaAnterior.subtract(despesaAnterior);
        DashboardResponse indicadores = new DashboardResponse(receita, despesa, resultado, margem(receita, resultado),
                receitaAnterior, despesaAnterior, anterior, margem(receitaAnterior, anterior), variacao(receitaAnterior, receita), variacao(despesaAnterior, despesa), variacao(anterior, resultado));
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        List<Mes> meses = mensal.entrySet().stream().map(e -> new Mes(e.getKey().toString(), e.getValue()[0], e.getValue()[1], e.getValue()[0].subtract(e.getValue()[1]), e.getKey().equals(YearMonth.from(hoje)))).toList();
        List<Variacao> mudancas = variacoes.entrySet().stream().map(e -> new Variacao(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[1].subtract(e.getValue()[0])))
                .filter(v -> v.diferenca().signum() != 0).sorted(Comparator.comparing((Variacao v) -> v.diferenca().abs()).reversed()).toList();
        return new DashboardAnalysisResponse(clienteId, inicio, fim, inicioAnterior, fimAnterior, hoje, filtro, indicadores, List.copyOf(categorias), meses, mudancas, List.copyOf(itens));
    }

    private String tipo(FinancialTransaction t) {
        return t.getTipo() == null ? "" : t.getTipo().trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal margem(BigDecimal receita, BigDecimal resultado) {
        return receita.signum() == 0 ? BigDecimal.ZERO : resultado.multiply(BigDecimal.valueOf(100)).divide(receita, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal variacao(BigDecimal anterior, BigDecimal atual) {
        return anterior.signum() == 0 ? null : atual.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior.abs(), 2, RoundingMode.HALF_UP);
    }
}
