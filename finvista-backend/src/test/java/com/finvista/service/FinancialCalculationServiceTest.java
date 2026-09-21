package com.finvista.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinancialCalculationServiceTest {

    private FinancialCalculationService service;

    @BeforeEach
    void setUp() {
        service = new FinancialCalculationService();
    }

    @Test
    void deveCalcularResultado() {
        BigDecimal resultado =
                service.calcularResultado(
                        new BigDecimal("150000.00"),
                        new BigDecimal("92000.00")
                );

        assertEquals(
                0,
                resultado.compareTo(
                        new BigDecimal("58000.00")
                )
        );
    }

    @Test
    void deveCalcularMargem() {
        BigDecimal margem =
                service.calcularMargem(
                        new BigDecimal("150000.00"),
                        new BigDecimal("58000.00")
                );

        assertEquals(
                new BigDecimal("38.67"),
                margem
        );
    }

    @Test
    void deveCalcularSaldoFuturo() {
        BigDecimal saldo =
                service.calcularSaldoFuturo(
                        new BigDecimal("10000.00"),
                        new BigDecimal("150000.00"),
                        new BigDecimal("92000.00")
                );

        assertEquals(
                0,
                saldo.compareTo(
                        new BigDecimal("68000.00")
                )
        );
    }

    @Test
    void deveCalcularTotalPonderadoDosOrcamentos() {

        BigDecimal total =
                BigDecimal.ZERO;

        total = total.add(
                service.calcularValorPonderado(
                        new BigDecimal("120000.00"),
                        70
                )
        );

        total = total.add(
                service.calcularValorPonderado(
                        new BigDecimal("85000.00"),
                        80
                )
        );

        total = total.add(
                service.calcularValorPonderado(
                        new BigDecimal("150000.00"),
                        50
                )
        );

        total = total.add(
                service.calcularValorPonderado(
                        new BigDecimal("65000.00"),
                        30
                )
        );

        total = total.add(
                service.calcularValorPonderado(
                        new BigDecimal("95000.00"),
                        90
                )
        );

        assertEquals(
                0,
                total.compareTo(
                        new BigDecimal("332000.00")
                )
        );
    }

    @Test
    void deveCalcularPercentualUtilizadoDaMeta() {
        BigDecimal percentual =
                service.calcularPercentualUtilizado(
                        new BigDecimal("75000.00"),
                        new BigDecimal("100000.00")
                );

        assertEquals(
                new BigDecimal("75.00"),
                percentual
        );
    }

    @Test
    void deveRetornarSaldoNegativoQuandoMetaForExcedida() {
        BigDecimal saldo =
                service.calcularSaldoMeta(
                        new BigDecimal("100000.00"),
                        new BigDecimal("115000.00")
                );

        assertEquals(
                0,
                saldo.compareTo(
                        new BigDecimal("-15000.00")
                )
        );
    }

    @Test
    void deveRejeitarProbabilidadeAcimaDeCem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calcularValorPonderado(
                        new BigDecimal("1000.00"),
                        101
                )
        );
    }

    @Test
    void deveRejeitarMetaZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calcularPercentualUtilizado(
                        new BigDecimal("500.00"),
                        BigDecimal.ZERO
                )
        );
    }
}