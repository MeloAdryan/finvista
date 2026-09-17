package com.finvista.controller;

import com.finvista.model.FinancialHistory;
import com.finvista.repository.FinancialHistoryRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final FinancialHistoryRepository repository;

    public DashboardController(
            FinancialHistoryRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> getDashboard() {

        FinancialHistory ultimoRegistro =
                repository
                        .findFirstByOrderByPeriodoDesc()
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Nenhum histórico financeiro encontrado."
                                )
                        );

        BigDecimal receita =
                ultimoRegistro.getReceita();

        BigDecimal despesa =
                ultimoRegistro.getDespesa();

        BigDecimal resultado =
                receita.subtract(despesa);

        BigDecimal margem = BigDecimal.ZERO;

        if (receita.compareTo(BigDecimal.ZERO) > 0) {
            margem = resultado
                    .divide(
                            receita,
                            4,
                            RoundingMode.HALF_UP
                    )
                    .multiply(
                            new BigDecimal("100")
                    )
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        Map<String, Object> dados =
                new HashMap<>();

        dados.put("receita", receita);
        dados.put("despesa", despesa);
        dados.put("resultado", resultado);
        dados.put("margem", margem);

        return dados;
    }
}