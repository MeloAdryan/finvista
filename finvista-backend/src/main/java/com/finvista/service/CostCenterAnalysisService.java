package com.finvista.service;

import com.finvista.dto.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class CostCenterAnalysisService {

    private final FinancialTransactionRepository transactions;
    private final CostCategoryClassificationRepository classifications;
    private final ClienteContextService context;
    private final FinancialReferenceService reference;
    private ExpenseAllocationService expenseAllocationService;

    @Autowired
    public CostCenterAnalysisService(FinancialTransactionRepository transactions,
            CostCategoryClassificationRepository classifications, ClienteContextService context,
            FinancialReferenceService reference, ExpenseAllocationService allocations) {
        this(transactions, classifications, context, reference);
        this.expenseAllocationService = Objects.requireNonNull(allocations);
    }

    public CostCenterAnalysisService(FinancialTransactionRepository transactions,
            CostCategoryClassificationRepository classifications,
            ClienteContextService context, FinancialReferenceService reference) {
        this.transactions = transactions;
        this.classifications = classifications;
        this.context = context;
        this.reference = reference;
    }

    public CostCenterAnalysisResponse analisar(
            LocalDate inicio,
            LocalDate fim,
            CostBehavior comportamento,
            CostNature natureza
    ) {
        if (inicio == null && fim == null) {
            YearMonth mes = reference.obterMesReferencia();
            inicio = mes.atDay(1);
            fim = mes.atEndOfMonth();
        }

        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new IllegalArgumentException(
                    "Informe as duas datas; a final deve ser igual ou posterior à inicial."
            );
        }

        Long clienteId = context.getClienteAtualId();

        Map<String, CostCategoryClassification> mapa
                = mapaClassificacoes(clienteId);

        Map<String, BigDecimal> totais = new HashMap<>();

        // Variáveis finais para utilizar as datas dentro do filtro.
        final LocalDate de = inicio;
        final LocalDate ate = fim;

        List<FinancialTransaction> despesas = transactions
                .findByClienteIdAndDataBetweenOrderByDataAsc(
                        clienteId,
                        de,
                        ate
                )
                .stream()
                .filter(lancamento
                        -> despesaValida(lancamento)
                && lancamento.getData() != null
                && !lancamento.getData().isBefore(de)
                && !lancamento.getData().isAfter(ate)
                )
                .toList();

        // Classifica e filtra cada parcela, em vez do lançamento inteiro.
        for (ExpenseAllocationService.Parte parte : partes(clienteId, despesas)) {
            CostCategoryClassification classificacao
                    = mapa.get(chave(parte.categoria()));

            CostBehavior comportamentoDaParte = classificacao == null
                    ? CostBehavior.NAO_CLASSIFICADO
                    : classificacao.getComportamento();

            CostNature naturezaDaParte = classificacao == null
                    ? CostNature.NAO_CLASSIFICADO
                    : classificacao.getNatureza();

            if ((comportamento != null
                    && comportamento != comportamentoDaParte)
                    || (natureza != null
                    && natureza != naturezaDaParte)) {
                continue;
            }

            totais.merge(
                    parte.centroCusto(),
                    parte.valor(),
                    BigDecimal::add
            );
        }

        List<CostCenterResponse> centros = totais.entrySet()
                .stream()
                .sorted(
                        Map.Entry.<String, BigDecimal>comparingByValue()
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .map(entrada -> new CostCenterResponse(
                entrada.getKey(),
                entrada.getValue()
        ))
                .toList();

        BigDecimal total = centros.stream()
                .map(CostCenterResponse::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CostCenterAnalysisResponse(
                inicio,
                fim,
                total,
                centros
        );
    }

    public List<CostClassificationDto> listarClassificacoes() {
        Long clienteId = context.getClienteAtualId();
        Map<String, CostCategoryClassification> mapa = mapaClassificacoes(clienteId);
        Map<String, String> nomes = categoriasDoCliente(clienteId);
        return nomes.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(e -> {
                    CostCategoryClassification c = mapa.get(e.getKey());
                    return new CostClassificationDto(e.getValue(),
                            c == null ? CostBehavior.NAO_CLASSIFICADO : c.getComportamento(),
                            c == null ? CostNature.NAO_CLASSIFICADO : c.getNatureza());
                }).toList();
    }

    @Transactional
    public CostClassificationDto salvar(CostClassificationDto dto) {
        context.validarAdministrador();
        if (dto == null || dto.categoria() == null || dto.categoria().isBlank()
                || dto.comportamento() == null || dto.natureza() == null) {
            throw new IllegalArgumentException("Informe categoria, comportamento e natureza.");
        }
        Long clienteId = context.getClienteAtualId();
        String chave = chave(dto.categoria());
        Map<String, String> nomes = categoriasDoCliente(clienteId);
        if (!nomes.containsKey(chave)) {
            throw new IllegalArgumentException("Categoria não encontrada nas despesas do cliente selecionado.");
        }
        CostCategoryClassification c = classifications.findByClienteIdAndCategoriaChave(clienteId, chave)
                .orElseGet(CostCategoryClassification::new);
        c.setClienteId(clienteId);
        c.setCategoriaChave(chave);
        c.setComportamento(dto.comportamento());
        c.setNatureza(dto.natureza());
        classifications.save(c);
        return new CostClassificationDto(nomes.get(chave), c.getComportamento(), c.getNatureza());
    }

    private Map<String, CostCategoryClassification> mapaClassificacoes(Long clienteId) {
        Map<String, CostCategoryClassification> mapa = new HashMap<>();
        for (CostCategoryClassification c : classifications.findByClienteId(clienteId)) {
            mapa.put(c.getCategoriaChave(), c);
        }
        return mapa;
    }

    private Map<String, String> categoriasDoCliente(Long clienteId) {
        Map<String, String> nomes = new TreeMap<>();
        List<FinancialTransaction> despesas = transactions.findAllByClienteIdOrderByDataDesc(clienteId)
                .stream().filter(this::despesaValida).toList();
        for (ExpenseAllocationService.Parte parte : partes(clienteId, despesas)) {
            String categoria = parte.categoria();
            if (!ExpenseAllocationService.SEM_CATEGORIA.equals(categoria)
                    && !ExpenseAllocationService.CATEGORIA_REVISAR.equals(categoria)) {
                nomes.putIfAbsent(chave(categoria), categoria);
            }
        }
        return nomes;
    }

    private boolean despesaValida(FinancialTransaction t) {
        return t.getValor() != null && t.getTipo() != null && "DESPESA".equalsIgnoreCase(t.getTipo().trim());
    }

    private String chave(String valor) {
        return valor == null || valor.isBlank() ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private List<ExpenseAllocationService.Parte> partes(Long clienteId, List<FinancialTransaction> despesas) {
        return expenseAllocationService == null
                ? ExpenseAllocationService.legado(despesas)
                : expenseAllocationService.dividir(clienteId, despesas);
    }

}
