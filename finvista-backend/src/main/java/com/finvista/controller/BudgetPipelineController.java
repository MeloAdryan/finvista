package com.finvista.controller;

import com.finvista.dto.BudgetPipelineResponse;
import com.finvista.service.BudgetPipelineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orcamentos")
public class BudgetPipelineController {

    private final BudgetPipelineService budgetPipelineService;

    public BudgetPipelineController(
            BudgetPipelineService budgetPipelineService
    ) {
        this.budgetPipelineService =
                budgetPipelineService;
    }

    @GetMapping
    public List<BudgetPipelineResponse> getOrcamentos() {
        return budgetPipelineService.listar();
    }
}