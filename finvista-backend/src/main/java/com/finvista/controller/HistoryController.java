package com.finvista.controller;

import com.finvista.model.FinancialHistory;
import com.finvista.repository.FinancialHistoryRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/historico")
public class HistoryController {

    private final FinancialHistoryRepository repository;

    private static final DateTimeFormatter FORMATO_ENTRADA =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private static final DateTimeFormatter FORMATO_SAIDA =
            DateTimeFormatter.ofPattern(
                    "MMM/yyyy",
                    new Locale("pt", "BR")
            );

    public HistoryController(
            FinancialHistoryRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> getHistorico(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim
    ) {

        List<FinancialHistory> registros =
                repository.findAllByOrderByPeriodoAsc();

        List<Map<String, Object>> resultado =
                new ArrayList<>();

        for (FinancialHistory registro : registros) {

            YearMonth periodo =
                    YearMonth.parse(
                            registro.getPeriodo(),
                            FORMATO_ENTRADA
                    );

            YearMonth periodoInicial =
                    converterPeriodo(inicio);

            YearMonth periodoFinal =
                    converterPeriodo(fim);

            boolean depoisDoInicio =
                    periodoInicial == null ||
                    !periodo.isBefore(periodoInicial);

            boolean antesDoFim =
                    periodoFinal == null ||
                    !periodo.isAfter(periodoFinal);

            if (depoisDoInicio && antesDoFim) {
                resultado.add(
                        criarResposta(registro, periodo)
                );
            }
        }

        return resultado;
    }

    private YearMonth converterPeriodo(String periodo) {

        if (periodo == null || periodo.isBlank()) {
            return null;
        }

        return YearMonth.parse(
                periodo,
                FORMATO_ENTRADA
        );
    }

    private Map<String, Object> criarResposta(
            FinancialHistory registro,
            YearMonth periodo
    ) {

        Map<String, Object> dados =
                new HashMap<>();

        BigDecimal resultado =
                registro.getReceita()
                        .subtract(registro.getDespesa());

        BigDecimal margem = BigDecimal.ZERO;

        if (registro.getReceita()
                .compareTo(BigDecimal.ZERO) > 0) {

            margem = resultado
                    .divide(
                            registro.getReceita(),
                            4,
                            RoundingMode.HALF_UP
                    )
                    .multiply(
                            new BigDecimal("100")
                    );
        }

        String periodoFormatado =
                periodo
                        .format(FORMATO_SAIDA)
                        .replace(".", "");

        periodoFormatado =
                periodoFormatado
                        .substring(0, 1)
                        .toUpperCase()
                + periodoFormatado.substring(1);

        dados.put(
                "periodo",
                periodoFormatado
        );

        dados.put(
                "receita",
                registro.getReceita()
        );

        dados.put(
                "despesa",
                registro.getDespesa()
        );

        dados.put(
                "resultado",
                resultado
        );

        dados.put(
                "margem",
                margem
        );

        return dados;
    }
}