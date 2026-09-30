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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseDistributionServiceTest {

    private static final Long CLIENTE_ID = 1L;

    private FinancialTransactionRepository financialTransactionRepository;

    private ClienteContextService clienteContextService;

    private ExpenseDistributionService expenseDistributionService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository
                = mock(
                        FinancialTransactionRepository.class
                );

        clienteContextService
                = mock(
                        ClienteContextService.class
                );

        when(
                clienteContextService.getClienteAtualId()
        ).thenReturn(
                CLIENTE_ID
        );

        expenseDistributionService
                = new ExpenseDistributionService(
                        financialTransactionRepository,
                        clienteContextService
                );
    }

    @Test
    void deveAgruparDespesasPorCategoriaESomarValores() {

        FinancialTransaction materiaPrima1
                = criarDespesa(
                        "Compra de material",
                        "Matéria-prima",
                        "30000.00"
                );

        FinancialTransaction materiaPrima2
                = criarDespesa(
                        "Compra complementar",
                        "Matéria-prima",
                        "15000.00"
                );

        FinancialTransaction logistica
                = criarDespesa(
                        "Frete",
                        "Logística",
                        "18000.00"
                );

        when(
                financialTransactionRepository
                        .findByClienteIdAndTipoOrderByDataDesc(
                                CLIENTE_ID,
                                "DESPESA"
                        )
        ).thenReturn(
                List.of(
                        materiaPrima1,
                        materiaPrima2,
                        logistica
                )
        );

        List<ExpenseDistributionResponse> resultado
                = expenseDistributionService.listar();

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

        verify(
                financialTransactionRepository
        ).findByClienteIdAndTipoOrderByDataDesc(
                CLIENTE_ID,
                "DESPESA"
        );
    }

    @Test
    void deveAgruparCategoriaVaziaComoSemCategoria() {

        FinancialTransaction semCategoria
                = criarDespesa(
                        "Despesa sem categoria",
                        null,
                        "500.00"
                );

        when(
                financialTransactionRepository
                        .findByClienteIdAndTipoOrderByDataDesc(
                                CLIENTE_ID,
                                "DESPESA"
                        )
        ).thenReturn(
                List.of(semCategoria)
        );

        List<ExpenseDistributionResponse> resultado
                = expenseDistributionService.listar();

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
                        .findByClienteIdAndTipoOrderByDataDesc(
                                CLIENTE_ID,
                                "DESPESA"
                        )
        ).thenReturn(
                List.of()
        );

        List<ExpenseDistributionResponse> resultado
                = expenseDistributionService.listar();

        assertEquals(
                0,
                resultado.size()
        );
    }

    @Test
    void deveListarCategoriasDoCentroDeCustoSomenteDoClienteAutenticado() {

        FinancialTransaction primeira
                = criarDespesa(
                        "Servidor",
                        "Infraestrutura",
                        "5000.00"
                );

        primeira.setCentroCusto(
                "Tecnologia"
        );

        FinancialTransaction segunda
                = criarDespesa(
                        "Licença",
                        "Software",
                        "2000.00"
                );

        segunda.setCentroCusto(
                "Tecnologia"
        );

        FinancialTransaction outroCentro
                = criarDespesa(
                        "Publicidade",
                        "Marketing",
                        "1000.00"
                );

        outroCentro.setCentroCusto(
                "Comercial"
        );

        when(
                financialTransactionRepository
                        .findByClienteIdAndTipoOrderByDataDesc(
                                CLIENTE_ID,
                                "DESPESA"
                        )
        ).thenReturn(
                List.of(
                        primeira,
                        segunda,
                        outroCentro
                )
        );

        List<String> resultado
                = expenseDistributionService
                        .listarCategoriasPorCentroCusto(
                                "Tecnologia"
                        );

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                "Infraestrutura",
                resultado.get(0)
        );

        assertEquals(
                "Software",
                resultado.get(1)
        );

        verify(
                financialTransactionRepository
        ).findByClienteIdAndTipoOrderByDataDesc(
                CLIENTE_ID,
                "DESPESA"
        );
    }

    @Test
    void naoDeveConsultarBancoQuandoCentroCustoForVazio() {

        List<String> resultado
                = expenseDistributionService
                        .listarCategoriasPorCentroCusto(
                                "   "
                        );

        assertEquals(
                0,
                resultado.size()
        );

        verify(
                financialTransactionRepository,
                never()
        ).findByClienteIdAndTipoOrderByDataDesc(
                CLIENTE_ID,
                "DESPESA"
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
