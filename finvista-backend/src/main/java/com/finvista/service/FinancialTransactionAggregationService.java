package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialTransactionAggregationService {

    private static final String TIPO_RECEITA =
            "RECEITA";

    private static final String TIPO_DESPESA =
            "DESPESA";

    private final FinancialTransactionRepository
            financialTransactionRepository;

    public FinancialTransactionAggregationService(
            FinancialTransactionRepository financialTransactionRepository
    ) {
        this.financialTransactionRepository =
                financialTransactionRepository;
    }

    public List<MonthlyFinancialSummary>
    obterResumoMensal(
            LocalDate dataInicial,
            LocalDate dataFinal
    ) {
        validarPeriodo(
                dataInicial,
                dataFinal
        );

        List<FinancialTransaction> lancamentos =
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                dataInicial,
                                dataFinal
                        );

        Map<YearMonth, MonthlyAccumulator> meses =
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
                    new MonthlyAccumulator()
            );

            mesAtual =
                    mesAtual.plusMonths(1);
        }

        for (FinancialTransaction lancamento : lancamentos) {

            if (lancamento.getData() == null
                    || lancamento.getValor() == null
                    || lancamento.getTipo() == null) {

                continue;
            }

            YearMonth periodo =
                    YearMonth.from(
                            lancamento.getData()
                    );

            MonthlyAccumulator acumulador =
                    meses.get(periodo);

            if (acumulador == null) {
                continue;
            }

            String tipo =
                    lancamento.getTipo()
                            .trim()
                            .toUpperCase();

            if (TIPO_RECEITA.equals(tipo)) {

                acumulador.receita =
                        acumulador.receita.add(
                                lancamento.getValor()
                        );

            } else if (TIPO_DESPESA.equals(tipo)) {

                acumulador.despesa =
                        acumulador.despesa.add(
                                lancamento.getValor()
                        );
            }
        }

        List<MonthlyFinancialSummary> resultado =
                new ArrayList<>();

        for (
                Map.Entry<
                        YearMonth,
                        MonthlyAccumulator
                > entry : meses.entrySet()
        ) {

            resultado.add(
                    new MonthlyFinancialSummary(
                            entry.getKey(),
                            entry.getValue().receita,
                            entry.getValue().despesa
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

    public record MonthlyFinancialSummary(
            YearMonth periodo,
            BigDecimal receita,
            BigDecimal despesa
    ) {
    }

    private static class MonthlyAccumulator {

        private BigDecimal receita =
                BigDecimal.ZERO;

        private BigDecimal despesa =
                BigDecimal.ZERO;
    }
}