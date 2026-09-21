package com.finvista.service;

import com.finvista.dto.BudgetPipelineResponse;
import com.finvista.model.Budget;
import com.finvista.repository.BudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetPipelineServiceTest {

    private BudgetRepository budgetRepository;
    private BudgetPipelineService budgetPipelineService;

    @BeforeEach
    void setUp() {
        budgetRepository =
                mock(BudgetRepository.class);

        FinancialCalculationService calculationService =
                new FinancialCalculationService();

        budgetPipelineService =
                new BudgetPipelineService(
                        budgetRepository,
                        calculationService
                );
    }

    @Test
    void deveListarOrcamentosComValorPonderado() {

        Budget primeiro =
                new Budget(
                        "Cliente A",
                        new BigDecimal("100000.00"),
                        "EM_NEGOCIACAO",
                        80
                );

        Budget segundo =
                new Budget(
                        "Cliente B",
                        new BigDecimal("50000.00"),
                        "PROPOSTA",
                        50
                );

        when(
                budgetRepository
                        .findAllByOrderByProbabilidadeDesc()
        ).thenReturn(
                List.of(
                        primeiro,
                        segundo
                )
        );

        List<BudgetPipelineResponse> resultado =
                budgetPipelineService.listar();

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                "Cliente A",
                resultado.get(0).cliente()
        );

        assertEquals(
                new BigDecimal("100000.00"),
                resultado.get(0).valor()
        );

        assertEquals(
                80,
                resultado.get(0).probabilidade()
        );

        assertEquals(
                new BigDecimal("80000.00"),
                resultado.get(0).valorPonderado()
        );

        assertEquals(
                "Cliente B",
                resultado.get(1).cliente()
        );

        assertEquals(
                new BigDecimal("25000.00"),
                resultado.get(1).valorPonderado()
        );
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistiremOrcamentos() {

        when(
                budgetRepository
                        .findAllByOrderByProbabilidadeDesc()
        ).thenReturn(
                List.of()
        );

        List<BudgetPipelineResponse> resultado =
                budgetPipelineService.listar();

        assertEquals(
                0,
                resultado.size()
        );
    }
}