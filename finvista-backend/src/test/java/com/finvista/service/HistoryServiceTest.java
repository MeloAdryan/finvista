package com.finvista.service;

import com.finvista.dto.HistoryResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryServiceTest {

    private FinancialTransactionRepository
            financialTransactionRepository;

    private HistoryService historyService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository =
                mock(FinancialTransactionRepository.class);

        FinancialTransactionAggregationService
                transactionAggregationService =
                new FinancialTransactionAggregationService(
                        financialTransactionRepository
                );

        FinancialCalculationService calculationService =
                new FinancialCalculationService();

        historyService =
                new HistoryService(
                        transactionAggregationService,
                        calculationService
                );
    }

    @Test
    void deveCalcularResultadoMargemEFormatarPeriodo() {

        FinancialTransaction receita =
                criarLancamento(
                        LocalDate.of(2026, 9, 10),
                        "RECEITA",
                        "100000.00"
                );

        FinancialTransaction despesa =
                criarLancamento(
                        LocalDate.of(2026, 9, 15),
                        "DESPESA",
                        "40000.00"
                );

        configurarPeriodoCompleto(
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 15),
                List.of(
                        receita,
                        despesa
                )
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
                0,
                resposta.receita()
                        .compareTo(
                                new BigDecimal("100000.00")
                        )
        );

        assertEquals(
                0,
                resposta.despesa()
                        .compareTo(
                                new BigDecimal("40000.00")
                        )
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

        FinancialTransaction agosto =
                criarLancamento(
                        LocalDate.of(2026, 8, 10),
                        "RECEITA",
                        "80000.00"
                );

        FinancialTransaction setembro =
                criarLancamento(
                        LocalDate.of(2026, 9, 10),
                        "RECEITA",
                        "100000.00"
                );

        FinancialTransaction outubro =
                criarLancamento(
                        LocalDate.of(2026, 10, 10),
                        "RECEITA",
                        "120000.00"
                );

        configurarPeriodoCompleto(
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 10, 10),
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

        assertEquals(
                0,
                resultado.get(0)
                        .receita()
                        .compareTo(
                                new BigDecimal("100000.00")
                        )
        );
    }

    @Test
    void deveCalcularMargemZeroQuandoReceitaForZero() {

        FinancialTransaction despesa =
                criarLancamento(
                        LocalDate.of(2026, 9, 10),
                        "DESPESA",
                        "10000.00"
                );

        configurarPeriodoCompleto(
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 10),
                List.of(despesa)
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

    private void configurarPeriodoCompleto(
            LocalDate menorData,
            LocalDate maiorData,
            List<FinancialTransaction> lancamentos
    ) {

        when(
                financialTransactionRepository
                        .findMenorData()
        ).thenReturn(
                Optional.of(menorData)
        );

        when(
                financialTransactionRepository
                        .findMaiorData()
        ).thenReturn(
                Optional.of(maiorData)
        );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                menorData,
                                maiorData
                        )
        ).thenReturn(lancamentos);
    }

    private FinancialTransaction criarLancamento(
            LocalDate data,
            String tipo,
            String valor
    ) {

        FinancialTransaction lancamento =
                new FinancialTransaction();

        lancamento.setData(data);
        lancamento.setDescricao(
                "Lançamento de teste"
        );
        lancamento.setTipo(tipo);
        lancamento.setValor(
                new BigDecimal(valor)
        );

        return lancamento;
    }
}