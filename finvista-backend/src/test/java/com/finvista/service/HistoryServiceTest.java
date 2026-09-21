package com.finvista.service;

import com.finvista.dto.HistoryResponse;
import com.finvista.model.FinancialHistory;
import com.finvista.repository.FinancialHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryServiceTest {

    private FinancialHistoryRepository financialHistoryRepository;
    private HistoryService historyService;

    @BeforeEach
    void setUp() {

        financialHistoryRepository =
                mock(FinancialHistoryRepository.class);

        FinancialCalculationService calculationService =
                new FinancialCalculationService();

        historyService =
                new HistoryService(
                        financialHistoryRepository,
                        calculationService
                );
    }

    @Test
    void deveCalcularResultadoMargemEFormatarPeriodo() {

        FinancialHistory registro =
                new FinancialHistory(
                        "2026-09",
                        new BigDecimal("100000.00"),
                        new BigDecimal("40000.00")
                );

        when(
                financialHistoryRepository
                        .findAllByOrderByPeriodoAsc()
        ).thenReturn(
                List.of(registro)
        );

        List<HistoryResponse> resultado =
                historyService.listar(
                        null,
                        null
                );

        assertEquals(
                1,
                resultado.size()
        );

        HistoryResponse resposta =
                resultado.get(0);

        assertEquals(
                "Set/2026",
                resposta.periodo()
        );

        assertEquals(
                new BigDecimal("100000.00"),
                resposta.receita()
        );

        assertEquals(
                new BigDecimal("40000.00"),
                resposta.despesa()
        );

        assertEquals(
                0,
                resposta.resultado()
                        .compareTo(
                                new BigDecimal("60000.00")
                        )
        );

        assertEquals(
                0,
                resposta.margem()
                        .compareTo(
                                new BigDecimal("60.00")
                        )
        );
    }

    @Test
    void deveFiltrarHistoricoPorPeriodoInicialEFinal() {

        FinancialHistory agosto =
                new FinancialHistory(
                        "2026-08",
                        new BigDecimal("80000.00"),
                        new BigDecimal("30000.00")
                );

        FinancialHistory setembro =
                new FinancialHistory(
                        "2026-09",
                        new BigDecimal("100000.00"),
                        new BigDecimal("40000.00")
                );

        FinancialHistory outubro =
                new FinancialHistory(
                        "2026-10",
                        new BigDecimal("120000.00"),
                        new BigDecimal("50000.00")
                );

        when(
                financialHistoryRepository
                        .findAllByOrderByPeriodoAsc()
        ).thenReturn(
                List.of(
                        agosto,
                        setembro,
                        outubro
                )
        );

        List<HistoryResponse> resultado =
                historyService.listar(
                        "2026-09",
                        "2026-09"
                );

        assertEquals(
                1,
                resultado.size()
        );

        assertEquals(
                "Set/2026",
                resultado.get(0).periodo()
        );
    }

    @Test
    void deveCalcularMargemZeroQuandoReceitaForZero() {

        FinancialHistory registro =
                new FinancialHistory(
                        "2026-09",
                        BigDecimal.ZERO,
                        new BigDecimal("10000.00")
                );

        when(
                financialHistoryRepository
                        .findAllByOrderByPeriodoAsc()
        ).thenReturn(
                List.of(registro)
        );

        List<HistoryResponse> resultado =
                historyService.listar(
                        null,
                        null
                );

        assertEquals(
                1,
                resultado.size()
        );

        assertEquals(
                0,
                resultado.get(0)
                        .resultado()
                        .compareTo(
                                new BigDecimal("-10000.00")
                        )
        );

        assertEquals(
                0,
                resultado.get(0)
                        .margem()
                        .compareTo(BigDecimal.ZERO)
        );
    }
}