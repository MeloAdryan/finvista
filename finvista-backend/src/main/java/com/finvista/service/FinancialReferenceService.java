package com.finvista.service;

import com.finvista.dto.FinancialReferenceResponse;
import org.springframework.stereotype.Service;
import java.time.ZoneId;
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
 * Retorna o mês atual no fuso de São Paulo,
 * independentemente das datas dos lançamentos.
 */
public YearMonth obterMesReferencia() {
    return YearMonth.now(ZoneId.of("America/Sao_Paulo"));
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