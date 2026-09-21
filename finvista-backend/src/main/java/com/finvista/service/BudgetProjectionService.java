package com.finvista.service;

import com.finvista.model.Budget;
import com.finvista.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BudgetProjectionService {

    private final BudgetRepository budgetRepository;

    private final FinancialCalculationService
            calculationService;

    public BudgetProjectionService(
            BudgetRepository budgetRepository,
            FinancialCalculationService calculationService
    ) {
        this.budgetRepository =
                budgetRepository;

        this.calculationService =
                calculationService;
    }

    public List<MonthlyBudgetProjection>
    obterProjecaoMensal(
            LocalDate dataInicial,
            LocalDate dataFinal
    ) {
        validarPeriodo(
                dataInicial,
                dataFinal
        );

        List<Budget> orcamentos =
                budgetRepository
                        .findByDataPrevisaoFechamentoBetweenOrderByDataPrevisaoFechamentoAsc(
                                dataInicial,
                                dataFinal
                        );

        Map<YearMonth, BigDecimal> meses =
                new LinkedHashMap<>();

        YearMonth primeiroMes =
                YearMonth.from(dataInicial);

        YearMonth ultimoMes =
                YearMonth.from(dataFinal);

        YearMonth mesAtual =
                primeiroMes;

        while (!mesAtual.isAfter(ultimoMes)) {

            meses.put(
                    mesAtual,
                    BigDecimal.ZERO
            );

            mesAtual =
                    mesAtual.plusMonths(1);
        }

        for (Budget orcamento : orcamentos) {

            if (orcamento.getDataPrevisaoFechamento()
                    == null) {
                continue;
            }

            YearMonth periodo =
                    YearMonth.from(
                            orcamento
                                    .getDataPrevisaoFechamento()
                    );

            if (!meses.containsKey(periodo)) {
                continue;
            }

            BigDecimal valorPonderado =
                    calculationService
                            .calcularValorPonderado(
                                    orcamento.getValor(),
                                    orcamento.getProbabilidade()
                            );

            meses.put(
                    periodo,
                    meses.get(periodo)
                            .add(valorPonderado)
            );
        }

        List<MonthlyBudgetProjection> resultado =
                new ArrayList<>();

        for (
                Map.Entry<YearMonth, BigDecimal>
                        entry : meses.entrySet()
        ) {

            resultado.add(
                    new MonthlyBudgetProjection(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }

        return resultado;
    }

    private void validarPeriodo(
            LocalDate dataInicial,
            LocalDate dataFinal
    ) {
        if (dataInicial == null
                || dataFinal == null) {

            throw new IllegalArgumentException(
                    "Data inicial e data final são obrigatórias."
            );
        }

        if (dataFinal.isBefore(dataInicial)) {

            throw new IllegalArgumentException(
                    "Data final não pode ser anterior à data inicial."
            );
        }
    }

    public record MonthlyBudgetProjection(
            YearMonth periodo,
            BigDecimal valorPonderado
    ) {
    }
}