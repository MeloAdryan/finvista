package com.finvista.controller;

import com.finvista.model.CashFlow;
import com.finvista.repository.CashFlowRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/fluxo-caixa")
public class CashFlowController {

    private static final BigDecimal SALDO_INICIAL =
            new BigDecimal("50000.00");

    private final CashFlowRepository repository;

    public CashFlowController(CashFlowRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> getFluxoCaixa() {

        List<CashFlow> registros =
                repository.findAllByOrderByPeriodoAsc();

        List<Map<String, Object>> fluxo =
                new ArrayList<>();

        BigDecimal saldoAtual = SALDO_INICIAL;

        for (CashFlow registro : registros) {

            BigDecimal saldoInicial = saldoAtual;

            BigDecimal saldoFinal =
                    saldoInicial
                            .add(registro.getEntradas())
                            .subtract(registro.getSaidas());

            Map<String, Object> dados =
                    new HashMap<>();

            dados.put(
                    "mes",
                    formatarMes(registro.getPeriodo())
            );

            dados.put(
                    "saldoInicial",
                    saldoInicial
            );

            dados.put(
                    "entradas",
                    registro.getEntradas()
            );

            dados.put(
                    "saidas",
                    registro.getSaidas()
            );

            dados.put(
                    "saldoFinal",
                    saldoFinal
            );

            fluxo.add(dados);

            saldoAtual = saldoFinal;
        }

        return fluxo;
    }

    private String formatarMes(String periodo) {

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