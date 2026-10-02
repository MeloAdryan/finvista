package com.finvista.service;

import com.finvista.dto.CashFlowResponse;
import com.finvista.service.FinancialTransactionAggregationService.MonthlyFinancialSummary;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CashFlowService {

    private static final int PERIODO_PADRAO_MESES = 6;

    private final FinancialTransactionAggregationService transactionAggregationService;

    private final FinancialReferenceService financialReferenceService;

    public CashFlowService(
        FinancialTransactionAggregationService
                transactionAggregationService,
        FinancialReferenceService
                financialReferenceService
) {
    this.transactionAggregationService =
            transactionAggregationService;

    this.financialReferenceService =
            financialReferenceService;
}

    public List<CashFlowResponse> obterFluxoCaixa() {

        return obterFluxoCaixa(
                String.valueOf(PERIODO_PADRAO_MESES)
        );
    }

    public List<CashFlowResponse> obterFluxoCaixa(
            String periodo
    ) {

        if (periodo == null || periodo.isBlank()) {
            periodo = String.valueOf(
                    PERIODO_PADRAO_MESES
            );
        }

        if ("todos".equalsIgnoreCase(periodo)) {
            return montarFluxoCompleto();
        }

        int quantidadeMeses
                = converterQuantidadeMeses(periodo);

        return montarFluxoPorPeriodo(
                quantidadeMeses
        );
    }

    private List<CashFlowResponse>
            montarFluxoPorPeriodo(
                    int quantidadeMeses
            ) {


        YearMonth mesReferencia =
        financialReferenceService
                .obterMesReferencia();
                

        YearMonth primeiroMes
                = mesReferencia.minusMonths(
                        quantidadeMeses - 1L
                );

        LocalDate dataInicial
                = primeiroMes.atDay(1);

        LocalDate dataFinal
                = mesReferencia.atEndOfMonth();

        List<MonthlyFinancialSummary> resumos
                = transactionAggregationService
                        .obterResumoMensal(
                                dataInicial,
                                dataFinal
                        );

        /*
         * Calculamos o saldo anterior ao intervalo
         * para que o saldo acumulado não comece
         * artificialmente em zero.
         */
        BigDecimal saldoAnterior
                = calcularSaldoAnterior(
                        dataInicial
                );

        return montarFluxo(
                resumos,
                saldoAnterior
        );
    }

    private List<CashFlowResponse>
            montarFluxoCompleto() {

        List<MonthlyFinancialSummary> resumos
                = transactionAggregationService
                        .obterResumoMensalCompleto();

        return montarFluxo(
                resumos,
                BigDecimal.ZERO
        );
    }

    private BigDecimal calcularSaldoAnterior(
            LocalDate dataInicial
    ) {

        LocalDate diaAnterior
                = dataInicial.minusDays(1);

        LocalDate menorData
                = transactionAggregationService
                        .obterMenorData();

        if (menorData == null
                || diaAnterior.isBefore(menorData)) {

            return BigDecimal.ZERO;
        }

        List<MonthlyFinancialSummary> anteriores
                = transactionAggregationService
                        .obterResumoMensal(
                                menorData,
                                diaAnterior
                        );

        BigDecimal saldo
                = BigDecimal.ZERO;

        for (MonthlyFinancialSummary resumo
                : anteriores) {

            saldo = saldo
                    .add(resumo.receita())
                    .subtract(resumo.despesa());
        }

        return saldo;
    }

    private List<CashFlowResponse> montarFluxo(
            List<MonthlyFinancialSummary> resumos,
            BigDecimal saldoInicial
    ) {

        List<CashFlowResponse> fluxo
                = new ArrayList<>();

        BigDecimal saldoAtual
                = saldoInicial != null
                        ? saldoInicial
                        : BigDecimal.ZERO;

        for (MonthlyFinancialSummary resumo
                : resumos) {

            BigDecimal saldoDoMes
                    = saldoAtual;

            BigDecimal entradas
                    = resumo.receita() != null
                    ? resumo.receita()
                    : BigDecimal.ZERO;

            BigDecimal saidas
                    = resumo.despesa() != null
                    ? resumo.despesa()
                    : BigDecimal.ZERO;

            BigDecimal saldoFinal
                    = saldoDoMes
                            .add(entradas)
                            .subtract(saidas);

            fluxo.add(
                    new CashFlowResponse(
                            formatarMes(
                                    resumo.periodo()
                            ),
                            saldoDoMes,
                            entradas,
                            saidas,
                            saldoFinal
                    )
            );

            saldoAtual
                    = saldoFinal;
        }

        return fluxo;
    }

    private int converterQuantidadeMeses(
            String periodo
    ) {

        try {

            int quantidade
                    = Integer.parseInt(
                            periodo.trim()
                    );

            if (quantidade <= 0) {
                throw new IllegalArgumentException(
                        "O período deve possuir pelo menos 1 mês."
                );
            }

            /*
             * Proteção para evitar consultas
             * acidentalmente gigantes.
             */
            if (quantidade > 120) {
                throw new IllegalArgumentException(
                        "O período máximo permitido é de 120 meses."
                );
            }

            return quantidade;

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Período inválido. Utilize um número de meses ou 'todos'."
            );
        }
    }

    private String formatarMes(
            YearMonth periodo
    ) {

        DateTimeFormatter formatter
                = DateTimeFormatter.ofPattern(
                        "MMM/yyyy",
                        new Locale("pt", "BR")
                );

        String mes
                = periodo
                        .format(formatter)
                        .replace(".", "");

        return mes
                .substring(0, 1)
                .toUpperCase()
                + mes.substring(1);
    }
}
