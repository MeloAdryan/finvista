package com.finvista.service;

import com.finvista.dto.CashFlowResponse;
import com.finvista.service.FinancialTransactionAggregationService.MonthlyFinancialSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CashFlowService {

    private final FinancialTransactionAggregationService transactionAggregationService;

    public CashFlowService(
            FinancialTransactionAggregationService transactionAggregationService
    ) {
        this.transactionAggregationService
                = transactionAggregationService;
    }

    public List<CashFlowResponse>
            obterFluxoCaixa() {

        List<MonthlyFinancialSummary> resumos
                = transactionAggregationService
                        .obterResumoMensalCompleto();

        List<CashFlowResponse> fluxo
                = new ArrayList<>();

        BigDecimal saldoAtual
                = BigDecimal.ZERO;

        for (MonthlyFinancialSummary resumo : resumos) {

            BigDecimal saldoInicial
                    = saldoAtual;

            BigDecimal entradas
                    = resumo.receita();

            BigDecimal saidas
                    = resumo.despesa();

            BigDecimal saldoFinal
                    = saldoInicial
                            .add(entradas)
                            .subtract(saidas);

            CashFlowResponse dados
                    = new CashFlowResponse(
                            formatarMes(resumo.periodo()),
                            saldoInicial,
                            entradas,
                            saidas,
                            saldoFinal
                    );

            fluxo.add(dados);

            saldoAtual
                    = saldoFinal;
        }

        return fluxo;
    }

    private String formatarMes(
            java.time.YearMonth periodo
    ) {

        DateTimeFormatter formatter
                = DateTimeFormatter.ofPattern(
                        "MMM/yyyy",
                        new Locale("pt", "BR")
                );
        
                 
        String mes =
                periodo.format(formatter)
                        .replace(".", "");

        return mes
                .substring(0, 1)
                .toUpperCase()
 
               + mes.substring(1);
    }
}