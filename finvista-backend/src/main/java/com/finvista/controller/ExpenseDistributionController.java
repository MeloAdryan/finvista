package com.finvista.controller;

import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.service.ExpenseDistributionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/distribuicao-despesas")
public class ExpenseDistributionController {

    private final ExpenseDistributionService expenseDistributionService;

    public ExpenseDistributionController(
            ExpenseDistributionService expenseDistributionService
    ) {
        this.expenseDistributionService =
                expenseDistributionService;
    }

    @GetMapping
    public List<ExpenseDistributionResponse> getDistribuicaoDespesas() {
        return expenseDistributionService.listar();
    }
}