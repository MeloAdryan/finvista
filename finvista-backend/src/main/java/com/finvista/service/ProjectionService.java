package com.finvista.service;

import com.finvista.dto.ProjectionResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectionService {

    private final FinancialTransactionAggregationService
            aggregationService;

    private final FinancialCalculationService
            calculationService;

    private final Clock clock;

    public ProjectionService(
            FinancialTransactionAggregationService aggregationService,
            FinancialCalculationService calculationService,
            Clock clock
    ) {
        this.aggregationService =
                aggregationService;

        this.calculationService =
                calculationService;

        this.clock =
                clock;
    }

    public List<ProjectionResponse> obterProjecao() {

        YearMonth mesAtual =
                YearMonth.now(clock);

        LocalDate dataInicial =
                mesAtual.atDay(1);

        LocalDate dataFinal =
                mesAtual
                        .plusMonths(5)
                        .atEndOfMonth();

        List<
                FinancialTransactionAggregationService
                        .MonthlyFinancialSummary
        > registros =
                aggregationService.obterResumoMensal(
                        dataInicial,
                        dataFinal
                );

        List<ProjectionResponse> projecao =
                new ArrayList<>();

        BigDecimal saldoAcumulado =
                BigDecimal.ZERO;

        for (
                FinancialTransactionAggregationService
                        .MonthlyFinancialSummary registro
                : registros
        ) {

            BigDecimal receitaRealizada =
                    registro.receita();

            BigDecimal receitaProjetada =
                    BigDecimal.ZERO;

            BigDecimal receitaTotal =
                    receitaRealizada.add(
                            receitaProjetada
                    );

            BigDecimal despesa =
                    registro.despesa();

            BigDecimal resultado =
                    calculationService.calcularResultado(
                            receitaTotal,
                            despesa
                    );

            BigDecimal margem =
                    calculationService.calcularMargem(
                            receitaTotal,
                            resultado
                    );

            saldoAcumulado =
                    calculationService.calcularSaldoFuturo(
                            saldoAcumulado,
                            receitaTotal,
                            despesa
                    );

            ProjectionResponse dados =
                    new ProjectionResponse(
                            formatarPeriodo(
                                    registro.periodo()
                            ),
                            registro.periodo().toString(),
                            receitaRealizada,
                            receitaProjetada,
                            receitaTotal,
                            despesa,
                            resultado,
                            margem,
                            saldoAcumulado
                    );

            projecao.add(dados);
        }

        return projecao;
    }

    private String formatarPeriodo(
            YearMonth periodo
    ) {

        int mes =
                periodo.getMonthValue();

        return switch (mes) {
            case 1 -> "Jan";
            case 2 -> "Fev";
            case 3 -> "Mar";
            case 4 -> "Abr";
            case 5 -> "Mai";
            case 6 -> "Jun";
            case 7 -> "Jul";
            case 8 -> "Ago";
            case 9 -> "Set";
            case 10 -> "Out";
            case 11 -> "Nov";
            case 12 -> "Dez";
            default -> periodo.toString();
        };
    }
}