package com.finvista.service;

import com.finvista.dto.CostCenterResponse;
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

class CostCenterServiceTest {

    private FinancialTransactionRepository financialTransactionRepository;
    private CostCenterService costCenterService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository
                = mock(FinancialTransactionRepository.class);

        costCenterService
                = new CostCenterService(
                        financialTransactionRepository
                );
    }

    @Test
    void deveAgruparESomarDespesasPorCentroDeCusto() {

        FinancialTransaction primeira
                = criarDespesa(
                        "Custo Operacional",
                        new BigDecimal("50000.00")
                );

        FinancialTransaction segunda
                = criarDespesa(
                        "Custo Operacional",
                        new BigDecimal("20000.00")
                );

        FinancialTransaction terceira
                = criarDespesa(
                        "Despesas Administrativas",
                        new BigDecimal("30000.00")
                );

        when(
                financialTransactionRepository
                        .findByTipoOrderByDataDesc("DESPESA")
        ).thenReturn(
                List.of(
                        primeira,
                        segunda,
                        terceira
                )
        );

        List<CostCenterResponse> resultado
                = costCenterService.listar();

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                "Custo Operacional",
                resultado.get(0).nome()
        );

        assertEquals(
                new BigDecimal("70000.00"),
                resultado.get(0).valor()
        );

        assertEquals(
                "Despesas Administrativas",
                resultado.get(1).nome()
        );

        assertEquals(
                new BigDecimal("30000.00"),
                resultado.get(1).valor()
        );
    }

    @Test
    void deveIgnorarLancamentosSemCentroDeCusto() {

        FinancialTransaction comCentro
                = criarDespesa(
                        "Despesas Financeiras",
                        new BigDecimal("10000.00")
                );

        FinancialTransaction semCentro
                = criarDespesa(
                        null,
                        new BigDecimal("5000.00")
                );

        when(
                financialTransactionRepository
                        .findByTipoOrderByDataDesc("DESPESA")
        ).thenReturn(
                List.of(
                        comCentro,
                        semCentro
                )
        );

        List<CostCenterResponse> resultado
                = costCenterService.listar();

        assertEquals(
                1,
                resultado.size()
        );

        assertEquals(
                "Despesas Financeiras",
                resultado.get(0).nome()
        );

        assertEquals(
                new BigDecimal("10000.00"),
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

        List<CostCenterResponse> resultado
                = costCenterService.listar();

        assertEquals(
                0,
                resultado.size()
        );
    }

    private FinancialTransaction criarDespesa(
            String centroCusto,
            BigDecimal valor
    ) {

        return new FinancialTransaction(
                LocalDate.of(2026, 9, 1),
                "Despesa de teste",
                "DESPESA",
                valor,
                "Categoria de teste",
                centroCusto,
                "TESTE",
                null
        );
    }
}
