package com.finvista.service;

import com.finvista.dto.CashFlowResponse;
import com.finvista.model.CashFlow;
import com.finvista.repository.CashFlowRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CashFlowService {

    private final CashFlowRepository repository;

    public CashFlowService(
            CashFlowRepository repository
    ) {
        this.repository = repository;
    }

    public List<CashFlowResponse>
    obterFluxoCaixa() {

        List<CashFlow> registros =
                repository.findAllByOrderByPeriodoAsc();

        List<CashFlowResponse> fluxo =
                new ArrayList<>();

        BigDecimal saldoAtual =
                BigDecimal.ZERO;

        for (CashFlow registro : registros) {

            BigDecimal saldoInicial =
                    saldoAtual;

            BigDecimal saldoFinal =
                    saldoInicial
                            .add(registro.getEntradas())
                            .subtract(registro.getSaidas());

            CashFlowResponse dados =
                    new CashFlowResponse(
                            formatarMes(
                                    registro.getPeriodo()
                            ),
                            saldoInicial,
                            registro.getEntradas(),
                            registro.getSaidas(),
                            saldoFinal
                    );

            fluxo.add(dados);

            saldoAtual =
                    saldoFinal;
        }

        return fluxo;
    }

    private String formatarMes(
            String periodo
    ) {

        YearMonth yearMonth =
                YearMonth.parse(periodo);

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "MMM",
                        new Locale("pt", "BR")
                );

        String mes =
                yearMonth.format(formatter);

        return mes
                .substring(0, 1)
                .toUpperCase()
                + mes
                .substring(1)
                .replace(".", "");
    }
}