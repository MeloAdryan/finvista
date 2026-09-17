package com.finvista.controller;

import com.finvista.model.CostCenter;
import com.finvista.repository.CostCenterRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/centros-custo")
public class CostCenterController {

    private final CostCenterRepository repository;

    public CostCenterController(CostCenterRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> getCentrosCusto() {

        List<CostCenter> registros =
                repository.findAllByOrderByValorDesc();

        List<Map<String, Object>> centrosCusto =
                new ArrayList<>();

        for (CostCenter registro : registros) {

            Map<String, Object> dados =
                    new HashMap<>();

            dados.put(
                    "nome",
                    registro.getNome()
            );

            dados.put(
                    "valor",
                    registro.getValor()
            );

            centrosCusto.add(dados);
        }

        return centrosCusto;
    }
}