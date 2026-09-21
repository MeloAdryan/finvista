package com.finvista.service;

import com.finvista.model.Budget;
import com.finvista.repository.BudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetProjectionServiceTest {

    private BudgetRepository budgetRepository;

    private BudgetProjectionService service;

    @BeforeEach
    void setUp() {

        budgetRepository =
                mock(BudgetRepository.class);

        FinancialCalculationService
                calculationService =
                new FinancialCalculationService();

        service =
                new BudgetProjectionService(
                        budgetRepository,
                        calculationService
                );
    }

    @Test
    void deveAgruparValoresPonderadosPorMes() {

        List<Budget> orcamentos =
                List.of(
                        new Budget(
                                "Madeireira Norte",
                                new BigDecimal("120000.00"),
                                "Proposta enviada",
                                70,
                                LocalDate.of(2026, 11, 15)
                        ),
                        new Budget(
                                "Construtora Acre",
                                new BigDecimal("85000.00"),
                                "Negociação",
                                80,
                                LocalDate.of(2026, 12, 10)
                        ),
                        new Budget(
                                "Grupo Florestal",
                                new BigDecimal("150000.00"),
                                "Em análise",
                                50,
                                LocalDate.of(2026, 12, 20)
                        ),
                        new Budget(
                                "Indústria Amazônia",
                                new BigDecimal("65000.00"),
                                "Contato inicial",
                                30,
                                LocalDate.of(2027, 1, 15)
                        ),
                        new Budget(
                                "Madeiras Brasil",
                                new BigDecimal("95000.00"),
                                "Negociação",
                                90,
                                LocalDate.of(2027, 2, 10)
                        )
                );

        when(
                budgetRepository
                        .findByDataPrevisaoFechamentoBetweenOrderByDataPrevisaoFechamentoAsc(
                                any(LocalDate.class),
                                any(LocalDate.class)
                        )
        ).thenReturn(orcamentos);

        List<
                BudgetProjectionService.MonthlyBudgetProjection
        > resultado =
                service.obterProjecaoMensal(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 2, 28)
                );

        assertEquals(6, resultado.size());

        assertEquals(
                YearMonth.of(2026, 9),
                resultado.get(0).periodo()
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        resultado.get(0).valorPonderado()
                )
        );

        assertEquals(
                0,
                new BigDecimal("84000.00").compareTo(
                        resultado.get(2).valorPonderado()
                )
        );

        assertEquals(
                0,
                new BigDecimal("143000.00").compareTo(
                        resultado.get(3).valorPonderado()
                )
        );

        assertEquals(
                0,
                new BigDecimal("19500.00").compareTo(
                        resultado.get(4).valorPonderado()
                )
        );

        assertEquals(
                0,
                new BigDecimal("85500.00").compareTo(
                        resultado.get(5).valorPonderado()
                )
        );
    }

    @Test
    void deveManterMesSemOrcamentoComValorZero() {

        when(
                budgetRepository
                        .findByDataPrevisaoFechamentoBetweenOrderByDataPrevisaoFechamentoAsc(
                                any(LocalDate.class),
                                any(LocalDate.class)
                        )
        ).thenReturn(List.of());

        List<
                BudgetProjectionService.MonthlyBudgetProjection
        > resultado =
                service.obterProjecaoMensal(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 31)
                );

        assertEquals(2, resultado.size());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        resultado.get(0).valorPonderado()
                )
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        resultado.get(1).valorPonderado()
                )
        );
    }

    @Test
    void deveRejeitarPeriodoInvalido() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.obterProjecaoMensal(
                        LocalDate.of(2027, 2, 1),
                        LocalDate.of(2026, 9, 1)
                )
        );
    }
}