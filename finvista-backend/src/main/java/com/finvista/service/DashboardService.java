package com.finvista.service;

import com.finvista.dto.DashboardResponse;
import com.finvista.service
        .FinancialTransactionAggregationService
        .MonthlyFinancialSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class DashboardService {

    private static final BigDecimal CEM =
            new BigDecimal("100");

    private final FinancialTransactionAggregationService
            transactionAggregationService;

    private final FinancialCalculationService
            calculationService;
    private final FinancialReferenceService
        financialReferenceService;

   public DashboardService(
        FinancialTransactionAggregationService
                transactionAggregationService,
        FinancialCalculationService
                calculationService,
        FinancialReferenceService
                financialReferenceService
) {
    this.transactionAggregationService =
            transactionAggregationService;

    this.calculationService =
            calculationService;

    this.financialReferenceService =
            financialReferenceService;
}

    public DashboardResponse obterDashboard() {

        YearMonth mesAtual =
        financialReferenceService
                .obterMesReferencia();
                

        YearMonth mesAnterior =
                mesAtual.minusMonths(1);

        LocalDate dataInicial =
                mesAnterior.atDay(1);

        LocalDate dataFinal =
                mesAtual.atEndOfMonth();

        List<MonthlyFinancialSummary> resumos =
                transactionAggregationService
                        .obterResumoMensal(
                                dataInicial,
                                dataFinal
                        );

        MonthlyFinancialSummary resumoMesAnterior =
                localizarResumo(
                        resumos,
                        mesAnterior
                );

        MonthlyFinancialSummary resumoMesAtual =
                localizarResumo(
                        resumos,
                        mesAtual
                );

        BigDecimal receitaMesAnterior =
                obterReceita(
                        resumoMesAnterior
                );

        BigDecimal despesaMesAnterior =
                obterDespesa(
                        resumoMesAnterior
                );

        BigDecimal receita =
                obterReceita(
                        resumoMesAtual
                );

        BigDecimal despesa =
                obterDespesa(
                        resumoMesAtual
                );

        BigDecimal resultadoMesAnterior =
                calculationService
                        .calcularResultado(
                                receitaMesAnterior,
                                despesaMesAnterior
                        );

        BigDecimal margemMesAnterior =
                calculationService
                        .calcularMargem(
                                receitaMesAnterior,
                                resultadoMesAnterior
                        );

        BigDecimal resultado =
                calculationService
                        .calcularResultado(
                                receita,
                                despesa
                        );

        BigDecimal margem =
                calculationService
                        .calcularMargem(
                                receita,
                                resultado
                        );

        BigDecimal variacaoReceita =
                calcularVariacaoPercentual(
                        receitaMesAnterior,
                        receita
                );

        BigDecimal variacaoDespesa =
                calcularVariacaoPercentual(
                        despesaMesAnterior,
                        despesa
                );

        BigDecimal variacaoResultado =
                calcularVariacaoPercentual(
                        resultadoMesAnterior,
                        resultado
                );

        return new DashboardResponse(
                receita,
                despesa,
                resultado,
                margem,

                receitaMesAnterior,
                despesaMesAnterior,
                resultadoMesAnterior,
                margemMesAnterior,

                variacaoReceita,
                variacaoDespesa,
                variacaoResultado
        );
    }

    private MonthlyFinancialSummary localizarResumo(
            List<MonthlyFinancialSummary> resumos,
            YearMonth periodo
    ) {

        if (resumos == null || resumos.isEmpty()) {
            return null;
        }

        return resumos
                .stream()
                .filter(
                        resumo ->
                                resumo != null
                                        && periodo.equals(
                                                resumo.periodo()
                                        )
                )
                .findFirst()
                .orElse(null);
    }

    private BigDecimal obterReceita(
            MonthlyFinancialSummary resumo
    ) {

        if (resumo == null
                || resumo.receita() == null) {

            return BigDecimal.ZERO;
        }

        return resumo.receita();
    }

    private BigDecimal obterDespesa(
            MonthlyFinancialSummary resumo
    ) {

        if (resumo == null
                || resumo.despesa() == null) {

            return BigDecimal.ZERO;
        }

        return resumo.despesa();
    }

    private BigDecimal calcularVariacaoPercentual(
            BigDecimal valorAnterior,
            BigDecimal valorAtual
    ) {

        BigDecimal anterior =
                valorAnterior != null
                        ? valorAnterior
                        : BigDecimal.ZERO;

        BigDecimal atual =
                valorAtual != null
                        ? valorAtual
                        : BigDecimal.ZERO;

        if (anterior.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            return BigDecimal.ZERO;
        }

        return atual
                .subtract(anterior)
                .divide(
                        anterior.abs(),
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(CEM)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }
}