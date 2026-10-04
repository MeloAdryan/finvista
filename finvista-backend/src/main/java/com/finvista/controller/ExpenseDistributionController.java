package com.finvista.controller;

import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.service.ExpenseDistributionService;
import com.finvista.service.FinancialReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/distribuicao-despesas")
public class ExpenseDistributionController {

    private final ExpenseDistributionService expenseDistributionService;

    private final FinancialReferenceService financialReferenceService;

    public ExpenseDistributionController(
            ExpenseDistributionService expenseDistributionService,
            FinancialReferenceService financialReferenceService
    ) {
        this.expenseDistributionService = expenseDistributionService;
        this.financialReferenceService = financialReferenceService;
    }


   @GetMapping
public List<ExpenseDistributionResponse> getDistribuicaoDespesas(
        @RequestParam(defaultValue = "false") boolean historico
) {
    if (historico) {
        return expenseDistributionService.listar();
    }

    return expenseDistributionService.listarNoMes(
            financialReferenceService.obterMesReferencia()
    );
}

    @GetMapping("/categorias")
    public List<String> getCategoriasPorCentroCusto(
            @RequestParam String centroCusto) {
        return expenseDistributionService
                .listarCategoriasPorCentroCusto(
                        centroCusto);
    }

}