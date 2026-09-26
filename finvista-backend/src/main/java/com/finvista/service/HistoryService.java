package com.finvista.service;

import com.finvista.dto.HistoryResponse;
import com.finvista.service.FinancialTransactionAggregationService.MonthlyFinancialSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class HistoryService {

    private static final DateTimeFormatter FORMATO_ENTRADA =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private static final DateTimeFormatter FORMATO_SAIDA =
            DateTimeFormatter.ofPattern(
                    "MMM/yyyy",
                    new Locale("pt", "BR")
            );

    private final FinancialTransactionAggregationService
            transactionAggregationService;

    private final FinancialCalculationService
            calculationService;

    public HistoryService(
            FinancialTransactionAggregationService transactionAggregationService,
            FinancialCalculationService calculationService
    ) {
        this.transactionAggregationService =
                transactionAggregationService;

        this.calculationService =
                calculationService;
    }

    public List<HistoryResponse> listar(
            String inicio,
            String fim
    ) {
        YearMonth periodoInicial =
                converterPeriodo(inicio);

        YearMonth periodoFinal =
                converterPeriodo(fim);

        validarPeriodo(
                periodoInicial,
                periodoFinal
        );

        return transactionAggregationService
                .obterResumoMensalCompleto()
                .stream()
                .filter(resumo ->
                        estaNoPeriodo(
                                resumo.periodo(),
                                periodoInicial,
                                periodoFinal
                        )
                )
                .map(this::criarResposta)
                .toList();
    }

    private boolean estaNoPeriodo(
            YearMonth periodo,
            YearMonth periodoInicial,
            YearMonth periodoFinal
    ) {
        boolean depoisDoInicio =
                periodoInicial == null
                        || !periodo.isBefore(periodoInicial);

        boolean antesDoFim =
                periodoFinal == null
                        || !periodo.isAfter(periodoFinal);

        return depoisDoInicio && antesDoFim;
    }

    private YearMonth converterPeriodo(
            String periodo
    ) {
        if (periodo == null || periodo.isBlank()) {
            return null;
        }

        return YearMonth.parse(
                periodo,
                FORMATO_ENTRADA
        );
    }

    private void validarPeriodo(
            YearMonth periodoInicial,
            YearMonth periodoFinal
    ) {
        if (periodoInicial != null
                && periodoFinal != null
                && periodoFinal.isBefore(periodoInicial)) {

            throw new IllegalArgumentException(
                    "Período final não pode ser anterior ao período inicial."
            );
        }
    }

    private HistoryResponse criarResposta(
            MonthlyFinancialSummary resumo
    ) {
        BigDecimal resultado =
                calculationService.calcularResultado(
                        resumo.receita(),
                        resumo.despesa()
                );

        BigDecimal margem =
                calculationService.calcularMargem(
                        resumo.receita(),
                        resultado
                );

        return new HistoryResponse(
                formatarPeriodo(resumo.periodo()),
                resumo.receita(),
                resumo.despesa(),
                resultado,
                margem
        );
    }

    private String formatarPeriodo(
            YearMonth periodo
    ) {
        String periodoFormatado =
                periodo
                        .format(FORMATO_SAIDA)
                        .replace(".", "");

        return periodoFormatado
                .substring(0, 1)
                .toUpperCase()
                + periodoFormatado.substring(1);
    }
}