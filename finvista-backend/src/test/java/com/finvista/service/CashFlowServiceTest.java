package com.finvista.service;

import com.finvista.model.CashFlow;
import com.finvista.repository.CashFlowRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class CashFlowServiceTest {

    private CashFlowRepository repository;
    private CashFlowService service;

    @BeforeEach
    void configurar() {

        repository =
                Mockito.mock(
                        CashFlowRepository.class
                );

        service =
                new CashFlowService(
                        repository
                );
    }

    @Test
    void deveCalcularFluxoCaixaPartindoDeZero() {

        List<CashFlow> registros =
                List.of(
                        new CashFlow(
                                "2026-09",
                                new BigDecimal("10000.00"),
                                new BigDecimal("4000.00")
                        ),
                        new CashFlow(
                                "2026-10",
                                new BigDecimal("5000.00"),
                                new BigDecimal("7000.00")
                        )
                );

        when(
                repository.findAllByOrderByPeriodoAsc()
        ).thenReturn(registros);

        var resultado =
                service.obterFluxoCaixa();

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(0).saldoInicial()
        );

        assertEquals(
                new BigDecimal("10000.00"),
                resultado.get(0).entradas()
        );

        assertEquals(
                new BigDecimal("4000.00"),
                resultado.get(0).saidas()
        );

        assertEquals(
                new BigDecimal("6000.00"),
                resultado.get(0).saldoFinal()
        );

        assertEquals(
                new BigDecimal("6000.00"),
                resultado.get(1).saldoInicial()
        );

        assertEquals(
                new BigDecimal("4000.00"),
                resultado.get(1).saldoFinal()
        );
    }

    @Test
    void deveRetornarListaVaziaSemRegistros() {

        when(
                repository.findAllByOrderByPeriodoAsc()
        ).thenReturn(List.of());

        var resultado =
                service.obterFluxoCaixa();

        assertEquals(
                0,
                resultado.size()
        );
    }
}