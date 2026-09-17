package com.finvista.controller;

import com.finvista.model.ExpenseDistribution;
import com.finvista.repository.ExpenseDistributionRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/distribuicao-despesas")
public class ExpenseDistributionController {

    private final ExpenseDistributionRepository repository;

    public ExpenseDistributionController(
            ExpenseDistributionRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> getDistribuicaoDespesas() {

        List<ExpenseDistribution> registros =
                repository.findAllByOrderByValorDesc();

        List<Map<String, Object>> despesas =
                new ArrayList<>();

        for (ExpenseDistribution registro : registros) {

            Map<String, Object> dados =
                    new HashMap<>();

            dados.put(
                    "categoria",
                    registro.getCategoria()
            );

            dados.put(
                    "valor",
                    registro.getValor()
            );

            despesas.add(dados);
        }

        return despesas;
    }
}