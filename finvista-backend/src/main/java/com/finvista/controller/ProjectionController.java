package com.finvista.controller;

import com.finvista.model.CashFlow;
import com.finvista.repository.CashFlowRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projecao")
public class ProjectionController {

    private final CashFlowRepository repository;

    public ProjectionController(
            CashFlowRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> getProjecao() {

        List<CashFlow> registros =
                repository.findAllByOrderByPeriodoAsc();

        List<Map<String, Object>> projecao =
                new ArrayList<>();

        BigDecimal saldoAcumulado =
                BigDecimal.ZERO;

        for (CashFlow registro : registros) {

            BigDecimal receita =
                    registro.getEntradas();

            BigDecimal despesa =
                    registro.getSaidas();

            BigDecimal resultado =
                    receita.subtract(despesa);

            saldoAcumulado =
                    saldoAcumulado.add(resultado);

            Map<String, Object> dados =
                    new HashMap<>();

            dados.put(
                    "mes",
                    formatarPeriodo(
                            registro.getPeriodo()
                    )
            );

            dados.put(
                    "receita",
                    receita
            );

            dados.put(
                    "despesa",
                    despesa
            );

            dados.put(
                    "resultado",
                    resultado
            );

            dados.put(
                    "saldo",
                    saldoAcumulado
            );

            projecao.add(dados);
        }

        return projecao;
    }

    private String formatarPeriodo(
            String periodo
    ) {

        String[] partes =
                periodo.split("-");

        int mes =
                Integer.parseInt(partes[1]);

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
            default -> periodo;
        };
    }
}