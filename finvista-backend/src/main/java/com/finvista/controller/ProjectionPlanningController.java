package com.finvista.controller;

import com.finvista.dto.ProjectionPlanningResponse;
import com.finvista.service.ProjectionPlanningService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/projecao/planejamento")
public class ProjectionPlanningController {

    private final ProjectionPlanningService service;

    public ProjectionPlanningController(ProjectionPlanningService service) {
        this.service = service;
    }

    @GetMapping
    public ProjectionPlanningResponse simular(
            @RequestParam(name = "receitaMensal", required = false) BigDecimal receitaMensal,
            @RequestParam(name = "saldoInicial", required = false) BigDecimal saldoInicial,
            @RequestParam(name = "variacaoPercentual", required = false) BigDecimal variacao
    ) {
        try {
            return service.simular(receitaMensal, saldoInicial, variacao);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
