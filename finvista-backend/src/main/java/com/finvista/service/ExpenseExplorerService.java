package com.finvista.service;

import com.finvista.dto.ExpenseExplorerResponse;
import com.finvista.dto.ExpenseExplorerResponse.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ExpenseExplorerService {

    private final FinancialTransactionRepository transactions;
    private final CostCategoryClassificationRepository classifications;
    private final ClienteContextService context;
    private final FinancialReferenceService reference;
    private final ExpenseAllocationService allocations;

    public ExpenseExplorerService(FinancialTransactionRepository transactions, CostCategoryClassificationRepository classifications,
            ClienteContextService context, FinancialReferenceService reference, ExpenseAllocationService allocations) {
        this.transactions = transactions;
        this.classifications = classifications;
        this.context = context;
        this.reference = reference;
        this.allocations = allocations;
    }

    @Transactional(readOnly = true)
    public ExpenseExplorerResponse consultar(LocalDate inicio, LocalDate fim, String centro, String categoria, CostBehavior comportamento, CostNature natureza) {
        if (inicio == null && fim == null) {
            var mes = reference.obterMesReferencia();
            inicio = mes.atDay(1);
            fim = mes.atEndOfMonth();
        }
        if (inicio == null || fim == null || fim.isBefore(inicio) || ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(fim)) > 23) {
            throw new IllegalArgumentException("Informe um intervalo válido de até 24 meses.");
        }
        centro = normalizar(centro);
        categoria = normalizar(categoria);
        if ((centro != null && centro.length() > 255) || (categoria != null && categoria.length() > 255)) {
            throw new IllegalArgumentException("Filtro inválido.");
        }
        LocalDate anteriorInicio, anteriorFim = inicio.minusDays(1);
        if (inicio.getDayOfMonth() == 1 && fim.equals(YearMonth.from(fim).atEndOfMonth())) {
            anteriorInicio = inicio.minusMonths(ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(fim)) + 1);
        }else {
            anteriorInicio = inicio.minusDays(ChronoUnit.DAYS.between(inicio, fim) + 1);
        }
        Long cliente = context.getClienteAtualId();
        Map<String, CostCategoryClassification> mapa = new HashMap<>();
        for (var c : classifications.findByClienteId(cliente)) {
            mapa.put(c.getCategoriaChave(), c);
        }
        LocalDate de = inicio, ate = fim, anteriorDe = anteriorInicio;
        var registros = transactions.findByClienteIdAndDataBetweenOrderByDataAsc(cliente, anteriorInicio, fim).stream()
                .filter(t -> t.getData() != null && !t.getData().isBefore(anteriorDe) && !t.getData().isAfter(ate) && t.getValor() != null && t.getTipo() != null && "DESPESA".equalsIgnoreCase(t.getTipo().trim())).toList();
        Map<String, BigDecimal[]> centros = new TreeMap<>(String.CASE_INSENSITIVE_ORDER), categorias = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SortedSet<String> catalogoCentros = new TreeSet<>(String.CASE_INSENSITIVE_ORDER), catalogoCategorias = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<FinancialTransaction, BigDecimal> selecionados = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO, totalAnterior = BigDecimal.ZERO;
        for (var detalhe : allocations.dividirDetalhado(cliente, registros)) {
            var p = detalhe.parte();
            var t = detalhe.lancamento();
            var c = mapa.get(chave(p.categoria()));
            var b = c == null ? CostBehavior.NAO_CLASSIFICADO : c.getComportamento();
            var n = c == null ? CostNature.NAO_CLASSIFICADO : c.getNatureza();
            if ((comportamento != null && comportamento != b) || (natureza != null && natureza != n)) {
                continue;
            }
            catalogoCentros.add(p.centroCusto());
            catalogoCategorias.add(p.categoria());
            if (!corresponde(centro, p.centroCusto()) || !corresponde(categoria, p.categoria())) {
                continue;
            }
            int indice = t.getData().isBefore(de) ? 0 : 1;
            centros.computeIfAbsent(p.centroCusto(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[indice] = centros.get(p.centroCusto())[indice].add(p.valor());
            categorias.computeIfAbsent(p.categoria(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[indice] = categorias.get(p.categoria())[indice].add(p.valor());
            if (indice == 1) {
                total = total.add(p.valor());
                selecionados.merge(t, p.valor(), BigDecimal::add);
            } else {
                totalAnterior = totalAnterior.add(p.valor());
            }
        }
        if (centro != null) {
            catalogoCentros.add(centro);

        }if (categoria != null) {
            catalogoCategorias.add(categoria);
        }
        var itens = selecionados.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().getData()))
                .map(e -> new Item(e.getKey().getId(), e.getKey().getData(), e.getKey().getDescricao(), e.getValue(), e.getKey().getValor())).toList();
        return new ExpenseExplorerResponse(inicio, fim, anteriorInicio, anteriorFim, centro, categoria, comportamento == null ? null : comportamento.name(), natureza == null ? null : natureza.name(), total, totalAnterior, List.copyOf(catalogoCentros), List.copyOf(catalogoCategorias), linhas(centros), linhas(categorias), itens);
    }

    private List<Linha> linhas(Map<String, BigDecimal[]> mapa) {
        return mapa.entrySet().stream().map(e -> new Linha(e.getKey(), e.getValue()[1], e.getValue()[0], e.getValue()[1].subtract(e.getValue()[0]))).sorted(Comparator.comparing(Linha::atual).reversed().thenComparing(Linha::nome)).toList();
    }

    private String normalizar(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String chave(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private boolean corresponde(String filtro, String valor) {
        return filtro == null || filtro.equalsIgnoreCase(valor);
    }
}
