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

    private static final Long CLIENTE_ID = 1L;

    private FinancialTransactionRepository financialTransactionRepository;

    private ClienteContextService clienteContextService;

    private HistoryService historyService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository = mock(
                FinancialTransactionRepository.class
        );

        clienteContextService = mock(
                ClienteContextService.class
        );

        /*
         * O FinancialTransactionAggregationService utiliza
         * getClienteAtualId() para determinar o cliente cujos
         * lançamentos financeiros devem ser consultados.
         */
        when(
                clienteContextService.getClienteAtualId()
        ).thenReturn(
                CLIENTE_ID
        );

        FinancialTransactionAggregationService transactionAggregationService =
                new FinancialTransactionAggregationService(
                        financialTransactionRepository,
                        clienteContextService
                );

        FinancialCalculationService calculationService =
                new FinancialCalculationService();

        historyService = new HistoryService(
                transactionAggregationService,
                calculationService
        );
    }

    @Test
    void deveCalcularResultadoMargemEFormatarPeriodo() {

        FinancialTransaction receita = criarLancamento(
                LocalDate.of(2026, 9, 10),
                "RECEITA",
                "100000.00"
        );

        FinancialTransaction despesa = criarLancamento(
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

        assertValor(
                "100000.00",
                resposta.receita()
        );

        assertValor(
                "40000.00",
                resposta.despesa()
        );

        assertValor(
                "60000.00",
                resposta.resultado()
        );

        assertValor(
                "60.00",
                resposta.margem()
        );
    }

    @Test
    void deveFiltrarHistoricoPorPeriodoInicialEFinal() {

        FinancialTransaction agosto = criarLancamento(
                LocalDate.of(2026, 8, 10),
                "RECEITA",
                "80000.00"
        );

        FinancialTransaction setembro = criarLancamento(
                LocalDate.of(2026, 9, 10),
                "RECEITA",
                "100000.00"
        );

        FinancialTransaction outubro = criarLancamento(
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

        assertValor(
                "100000.00",
                resultado.get(0).receita()
        );
    }

    @Test
    void deveCalcularMargemZeroQuandoReceitaForZero() {

        FinancialTransaction despesa = criarLancamento(
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

        assertValor(
                "-10000.00",
                resultado.get(0).resultado()
        );

        assertValor(
                "0",
                resultado.get(0).margem()
        );
    }

    private void configurarPeriodoCompleto(
            LocalDate menorData,
            LocalDate maiorData,
            List<FinancialTransaction> lancamentos
    ) {

        when(
                financialTransactionRepository
                        .findMenorDataByClienteId(
                                CLIENTE_ID
                        )
        ).thenReturn(
                Optional.of(menorData)
        );

        when(
                financialTransactionRepository
                        .findMaiorDataByClienteId(
                                CLIENTE_ID
                        )
        ).thenReturn(
                Optional.of(maiorData)
        );

        when(
                financialTransactionRepository
                        .findByClienteIdAndDataBetweenOrderByDataAsc(
                                CLIENTE_ID,
                                menorData,
                                maiorData
                        )
        ).thenReturn(
                lancamentos
        );
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

    private void assertValor(
            String esperado,
            BigDecimal atual
    ) {

        assertEquals(
                0,
                new BigDecimal(esperado)
                        .compareTo(atual)
        );
    }
}