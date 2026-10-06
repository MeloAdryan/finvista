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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CostCenterServiceTest {

    private static final Long CLIENTE_ID = 1L;

    private FinancialTransactionRepository financialTransactionRepository;
    private ClienteContextService clienteContextService;
    private CostCenterService costCenterService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository
                = mock(FinancialTransactionRepository.class);

        clienteContextService
                = mock(ClienteContextService.class);

        when(
                clienteContextService.getClienteAtualId()
        ).thenReturn(CLIENTE_ID);

        costCenterService
                = new CostCenterService(
                        financialTransactionRepository,
                        clienteContextService
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
                        .findByClienteIdAndTipoOrderByDataDesc(
                                CLIENTE_ID,
                                "DESPESA"
                        )
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

        verify(
                financialTransactionRepository
        ).findByClienteIdAndTipoOrderByDataDesc(
                CLIENTE_ID,
                "DESPESA"
        );
    }

    @Test
    void deveExibirLancamentosSemCentroDeCustoSemPerderSeuValor() {
        when(financialTransactionRepository
                .findByClienteIdAndTipoOrderByDataDesc(CLIENTE_ID, "DESPESA"))
                .thenReturn(List.of(
                        criarDespesa(
                                "Despesas Financeiras",
                                new BigDecimal("10000.00")
                        ),
                        criarDespesa(
                                null,
                                new BigDecimal("5000.00")
                        )
                ));

        List<CostCenterResponse> resultado = costCenterService.listar();

        assertEquals(2, resultado.size());

        assertEquals(
                "Despesas Financeiras",
                resultado.get(0).nome()
        );

        assertEquals(
                new BigDecimal("10000.00"),
                resultado.get(0).valor()
        );

        assertEquals(
                "Sem centro de custo",
                resultado.get(1).nome()
        );

        assertEquals(
                new BigDecimal("5000.00"),
                resultado.get(1).valor()
        );

        assertEquals(
                new BigDecimal("15000.00"),
                resultado.stream()
                        .map(CostCenterResponse::valor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
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
