package com.finvista.controller;

import com.finvista.model.Budget;
import com.finvista.repository.BudgetRepository;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orcamentos")
public class BudgetPipelineController {

    private final BudgetRepository repository;

    public BudgetPipelineController(
            BudgetRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> getOrcamentos() {

        List<Budget> registros =
                repository.findAllByOrderByProbabilidadeDesc();

        List<Map<String, Object>> orcamentos =
                new ArrayList<>();

        for (Budget registro : registros) {

            BigDecimal valorPonderado =
                    registro.getValor()
                            .multiply(
                                    BigDecimal.valueOf(
                                            registro.getProbabilidade()
                                    )
                            )
                            .divide(
                                    BigDecimal.valueOf(100),
                                    2,
                                    RoundingMode.HALF_UP
                            );

            Map<String, Object> dados =
                    new HashMap<>();

            dados.put("id", registro.getId());
            dados.put("cliente", registro.getCliente());
            dados.put("valor", registro.getValor());
            dados.put("status", registro.getStatus());
            dados.put(
                    "probabilidade",
                    registro.getProbabilidade()
            );
            dados.put(
                    "valorPonderado",
                    valorPonderado
            );

            orcamentos.add(dados);
        }

        return orcamentos;
    }
}