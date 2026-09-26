package com.finvista.service;

import com.finvista.dto.DashboardResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class DashboardService {

    private final FinancialTransactionAggregationService
            transactionAggregationService;

    private final FinancialCalculationService
            calculationService;

    public DashboardService(
            FinancialTransactionAggregationService transactionAggregationService,
            FinancialCalculationService calculationService
    ) {
        this.transactionAggregationService =
                transactionAggregationService;

        this.calculationService =
                calculationService;
    }

    public DashboardResponse obterDashboard() {

        YearMonth mesAtual =
                YearMonth.now();

        LocalDate dataInicial =
                mesAtual.atDay(1);

        LocalDate dataFinal =
                mesAtual.atEndOfMonth();

        List<FinancialTransactionAggregationService.MonthlyFinancialSummary>
                resumos =
                transactionAggregationService
                        .obterResumoMensal(
                                dataInicial,
                                dataFinal
                        );

        BigDecimal receita =
                BigDecimal.ZERO;

        BigDecimal despesa =
                BigDecimal.ZERO;

        if (!resumos.isEmpty()) {

            FinancialTransactionAggregationService.MonthlyFinancialSummary
                    resumo =
                    resumos.get(0);

            if (resumo.receita() != null) {
                receita =
                        resumo.receita();
            }

            if (resumo.despesa() != null) {
                despesa =
                        resumo.despesa();
            }
        }

        BigDecimal resultado =
                calculationService.calcularResultado(
                        receita,
                        despesa
                );

        BigDecimal margem =
                calculationService.calcularMargem(
                        receita,
                        resultado
                );

        return new DashboardResponse(
                receita,
                despesa,
                resultado,
                margem
        );
    }
}