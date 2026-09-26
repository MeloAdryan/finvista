package com.finvista.service;

import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpenseDistributionServiceTest {

    private FinancialTransactionRepository financialTransactionRepository;
    private ExpenseDistributionService expenseDistributionService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository =
                mock(FinancialTransactionRepository.class);

        expenseDistributionService =
                new ExpenseDistributionService(
                        financialTransactionRepository
                );
    }

    @Test
    void deveAgruparDespesasPorCategoriaESomarValores() {

        FinancialTransaction materiaPrima1 =
                criarDespesa(
                        "Compra de material",
                        "Matéria-prima",
                        "30000.00"
                );

        FinancialTransaction materiaPrima2 =
                criarDespesa(
                        "Compra complementar",
                        "Matéria-prima",
                        "15000.00"
                );

        FinancialTransaction logistica =
                criarDespesa(
                        "Frete",
                        "Logística",
                        "18000.00"
                );

        when(
                financialTransactionRepository
                        .findByTipoOrderByDataDesc("DESPESA")
        ).thenReturn(
                List.of(
                        materiaPrima1,
                        materiaPrima2,
                        logistica
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
    void deveAgruparCategoriaVaziaComoSemCategoria() {

        FinancialTransaction semCategoria =
                criarDespesa(
                        "Despesa sem categoria",
                        null,
                        "500.00"
                );

        when(
                financialTransactionRepository
                        .findByTipoOrderByDataDesc("DESPESA")
        ).thenReturn(
                List.of(semCategoria)
        );

        List<ExpenseDistributionResponse> resultado =
                expenseDistributionService.listar();

        assertEquals(
                1,
                resultado.size()
        );

        assertEquals(
                "Sem categoria",
                resultado.get(0).categoria()
        );

        assertEquals(
                new BigDecimal("500.00"),
                resultado.get(0).valor()
        );
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistiremDespesas() {

        when(
                financialTransactionRepository
                        .findByTipoOrderByDataDesc("DESPESA")
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

    private FinancialTransaction criarDespesa(
            String descricao,
            String categoria,
            String valor
    ) {

        return new FinancialTransaction(
                LocalDate.of(2026, 9, 1),
                descricao,
                "DESPESA",
                new BigDecimal(valor),
                categoria,
                null,
                "CONTA_AZUL_EXCEL",
                null
        );
    }
}