package com.finvista.service;

import com.finvista.dto.BudgetPipelineResponse;
import com.finvista.model.Budget;
import com.finvista.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BudgetPipelineService {

    private final BudgetRepository budgetRepository;
    private final FinancialCalculationService calculationService;

    public BudgetPipelineService(
            BudgetRepository budgetRepository,
            FinancialCalculationService calculationService
    ) {
        this.budgetRepository =
                budgetRepository;

        this.calculationService =
                calculationService;
    }

    public List<BudgetPipelineResponse> listar() {

        return budgetRepository
                .findAllByOrderByProbabilidadeDesc()
                .stream()
                .map(this::criarResposta)
                .toList();
    }

    private BudgetPipelineResponse criarResposta(
            Budget budget
    ) {
        BigDecimal valorPonderado =
                calculationService
                        .calcularValorPonderado(
                                budget.getValor(),
                                budget.getProbabilidade()
                        );

        return new BudgetPipelineResponse(
                budget.getId(),
                budget.getCliente(),
                budget.getValor(),
                budget.getStatus(),
                budget.getProbabilidade(),
                valorPonderado
        );
    }
}