package com.finvista.service;

import com.finvista.dto.DashboardResponse;
import com.finvista.model.FinancialHistory;
import com.finvista.repository.FinancialHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private FinancialHistoryRepository financialHistoryRepository;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {

        financialHistoryRepository =
                mock(FinancialHistoryRepository.class);

        FinancialCalculationService calculationService =
                new FinancialCalculationService();

        dashboardService =
                new DashboardService(
                        financialHistoryRepository,
                        calculationService
                );
    }

    @Test
    void deveMontarDashboardComUltimoRegistroFinanceiro() {

        FinancialHistory historico =
                new FinancialHistory(
                        "2026-09",
                        new BigDecimal("150000.00"),
                        new BigDecimal("92000.00")
                );

        when(
                financialHistoryRepository
                        .findFirstByOrderByPeriodoDesc()
        ).thenReturn(
                Optional.of(historico)
        );

        DashboardResponse resposta =
                dashboardService.obterDashboard();

        assertEquals(
                new BigDecimal("150000.00"),
                resposta.receita()
        );

        assertEquals(
                new BigDecimal("92000.00"),
                resposta.despesa()
        );

        assertEquals(
                0,
                resposta.resultado()
                        .compareTo(
                                new BigDecimal("58000.00")
                        )
        );

        assertEquals(
                0,
                resposta.margem()
                        .compareTo(
                                new BigDecimal("38.67")
                        )
        );
    }

    @Test
    void deveRetornarDashboardZeradoQuandoNaoExistirHistorico() {

        when(
                financialHistoryRepository
                        .findFirstByOrderByPeriodoDesc()
        ).thenReturn(
                Optional.empty()
        );

        DashboardResponse resposta =
                dashboardService.obterDashboard();

        assertEquals(
                0,
                resposta.receita()
                        .compareTo(BigDecimal.ZERO)
        );

        assertEquals(
                0,
                resposta.despesa()
                        .compareTo(BigDecimal.ZERO)
        );

        assertEquals(
                0,
                resposta.resultado()
                        .compareTo(BigDecimal.ZERO)
        );

        assertEquals(
                0,
                resposta.margem()
                        .compareTo(BigDecimal.ZERO)
        );
    }
}