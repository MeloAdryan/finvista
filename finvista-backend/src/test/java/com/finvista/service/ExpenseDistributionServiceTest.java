package com.finvista.service;

import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.model.ExpenseDistribution;
import com.finvista.repository.ExpenseDistributionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpenseDistributionServiceTest {

    private ExpenseDistributionRepository expenseDistributionRepository;
    private ExpenseDistributionService expenseDistributionService;

    @BeforeEach
    void setUp() {
        expenseDistributionRepository =
                mock(ExpenseDistributionRepository.class);

        expenseDistributionService =
                new ExpenseDistributionService(
                        expenseDistributionRepository
                );
    }

    @Test
    void deveListarDespesasOrdenadasPeloRepository() {

        ExpenseDistribution primeira =
                new ExpenseDistribution(
                        "Matéria-prima",
                        new BigDecimal("45000.00")
                );

        ExpenseDistribution segunda =
                new ExpenseDistribution(
                        "Logística",
                        new BigDecimal("18000.00")
                );

        when(
                expenseDistributionRepository
                        .findAllByOrderByValorDesc()
        ).thenReturn(
                List.of(
                        primeira,
                        segunda
                )
        );

        List<ExpenseDistributionResponse> resultado =
                expenseDistributionService.listar();

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                "Matéria-prima",
                resultado.get(0).categoria()
        );

        assertEquals(
                new BigDecimal("45000.00"),
                resultado.get(0).valor()
        );

        assertEquals(
                "Logística",
                resultado.get(1).categoria()
        );

        assertEquals(
                new BigDecimal("18000.00"),
                resultado.get(1).valor()
        );
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistiremDespesas() {

        when(
                expenseDistributionRepository
                        .findAllByOrderByValorDesc()
        ).thenReturn(
                List.of()
        );

        List<ExpenseDistributionResponse> resultado =
                expenseDistributionService.listar();

        assertEquals(
                0,
                resultado.size()
        );
    }
}