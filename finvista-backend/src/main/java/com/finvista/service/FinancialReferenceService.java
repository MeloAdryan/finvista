package com.finvista.service;

import com.finvista.dto.FinancialReferenceResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

@Service
public class FinancialReferenceService {

    private static final Locale LOCALE_PT_BR =
            Locale.forLanguageTag("pt-BR");

    private final FinancialTransactionAggregationService
            transactionAggregationService;

    public FinancialReferenceService(
            FinancialTransactionAggregationService
                    transactionAggregationService
    ) {
        this.transactionAggregationService =
                transactionAggregationService;
    }

    /**
     * Retorna o mês considerado como referência
     * financeira do cliente atual.
     *
     * Se existirem lançamentos, utiliza o mês do
     * lançamento mais recente.
     *
     * Caso contrário, utiliza o mês atual.
     */
    public YearMonth obterMesReferencia() {

        LocalDate maiorData =
                transactionAggregationService
                        .obterMaiorData();

        if (maiorData != null) {
            return YearMonth.from(maiorData);
        }

        return YearMonth.now();
    }

    public FinancialReferenceResponse obterReferencia() {

        YearMonth referencia =
                obterMesReferencia();

        String nomeMes =
                referencia
                        .getMonth()
                        .getDisplayName(
                                TextStyle.FULL,
                                LOCALE_PT_BR
                        );

        String nomeMesFormatado =
                Character.toUpperCase(
                        nomeMes.charAt(0)
                )
                + nomeMes.substring(1);

        String mesAno =
                nomeMesFormatado
                        + " "
                        + referencia.getYear();

        return new FinancialReferenceResponse(
                referencia.getYear(),
                referencia.getMonthValue(),
                mesAno,
                referencia.atEndOfMonth()
        );
    }
}