package com.finvista.service;

import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExpenseDistributionService {

    private static final String TIPO_DESPESA = "DESPESA";

    private static final String SEM_CATEGORIA = "Sem categoria";

    private final FinancialTransactionRepository financialTransactionRepository;

    private final ClienteContextService clienteContextService;

    public ExpenseDistributionService(
            FinancialTransactionRepository financialTransactionRepository,
            ClienteContextService clienteContextService
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.clienteContextService = clienteContextService;
    }

    public List<ExpenseDistributionResponse> listar() {

        Long clienteId = obterClienteIdAtual();

        List<FinancialTransaction> despesas
                = financialTransactionRepository
                        .findByClienteIdAndTipoOrderByDataDesc(
                                clienteId,
                                TIPO_DESPESA
                        );

        Map<String, BigDecimal> totaisPorCategoria
                = despesas.stream()
                        .collect(
                                Collectors.groupingBy(
                                        this::obterCategoria,
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                this::obterValor,
                                                BigDecimal::add
                                        )
                                )
                        );

        return totaisPorCategoria
                .entrySet()
                .stream()
                .map(
                        entry -> new ExpenseDistributionResponse(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .sorted(
                        Comparator.comparing(
                                ExpenseDistributionResponse::valor
                        ).reversed()
                )
                .toList();
    }

    public List<String> listarCategoriasPorCentroCusto(
            String centroCusto
    ) {

        if (centroCusto == null
                || centroCusto.isBlank()) {

            return List.of();
        }

        Long clienteId = obterClienteIdAtual();

        return financialTransactionRepository
                .findByClienteIdAndTipoOrderByDataDesc(
                        clienteId,
                        TIPO_DESPESA
                )
                .stream()
                .filter(
                        lancamento
                        -> lancamento.getCentroCusto() != null
                        && lancamento
                                .getCentroCusto()
                                .trim()
                                .equalsIgnoreCase(
                                        centroCusto.trim()
                                )
                )
                .map(FinancialTransaction::getCategoria)
                .filter(
                        categoria
                        -> categoria != null
                        && !categoria.isBlank()
                )
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Long obterClienteIdAtual() {
        return clienteContextService.getClienteAtualId();
    }

    private String obterCategoria(
            FinancialTransaction lancamento
    ) {

        String categoria = lancamento.getCategoria();

        if (categoria == null
                || categoria.isBlank()) {

            return SEM_CATEGORIA;
        }

        return categoria.trim();
    }

    private BigDecimal obterValor(
            FinancialTransaction lancamento
    ) {

        BigDecimal valor = lancamento.getValor();

        return valor != null
                ? valor
                : BigDecimal.ZERO;
    }

    public List<ExpenseDistributionResponse> listarNoMes(YearMonth mes) {
        if (mes == null) {
            throw new IllegalArgumentException("Mês de análise é obrigatório.");
        }

        Long clienteId = obterClienteIdAtual();

        List<FinancialTransaction> lancamentos = financialTransactionRepository
                .findByClienteIdAndDataBetweenOrderByDataAsc(
                        clienteId, mes.atDay(1), mes.atEndOfMonth()
                );

        Map<String, BigDecimal> totais = lancamentos.stream()
                .filter(lancamento -> lancamento.getData() != null
                && mes.equals(YearMonth.from(lancamento.getData()))
                && lancamento.getValor() != null
                && lancamento.getTipo() != null
                && TIPO_DESPESA.equalsIgnoreCase(
                        lancamento.getTipo().trim()
                ))
                .collect(Collectors.groupingBy(
                        this::obterCategoria,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                FinancialTransaction::getValor,
                                BigDecimal::add
                        )
                ));

        return totais.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entrada -> new ExpenseDistributionResponse(
                entrada.getKey(), entrada.getValue()
        ))
                .toList();
    }
}
