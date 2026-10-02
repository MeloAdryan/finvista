package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class CashFlowServiceTest {

    private static final Long CLIENTE_ID = 1L;

    private FinancialTransactionRepository financialTransactionRepository;

    private ClienteContextService clienteContextService;

    private FinancialReferenceService financialReferenceService;


    private CashFlowService service;

    @BeforeEach
    void configurar() {

        financialTransactionRepository = mock(
                FinancialTransactionRepository.class
        );

        clienteContextService = mock(
                ClienteContextService.class
        );

        financialReferenceService = mock(
        FinancialReferenceService.class
);

when(
        financialReferenceService.obterMesReferencia()
).thenReturn(
        YearMonth.of(2026, 10)
);

        when(
                clienteContextService.getClienteAtualId()
        ).thenReturn(CLIENTE_ID);

        FinancialTransactionAggregationService transactionAggregationService
                = new FinancialTransactionAggregationService(
                        financialTransactionRepository,
                        clienteContextService
                );

        service = new CashFlowService(
        transactionAggregationService,
        financialReferenceService
);
    }

    @Test
    void deveCalcularFluxoCaixaCompletoPartindoDeZero() {

        FinancialTransaction receitaSetembro
                = criarLancamento(
                        LocalDate.of(2026, 9, 10),
                        "RECEITA",
                        "10000.00"
                );

        FinancialTransaction despesaSetembro
                = criarLancamento(
                        LocalDate.of(2026, 9, 15),
                        "DESPESA",
                        "4000.00"
                );

        FinancialTransaction receitaOutubro
                = criarLancamento(
                        LocalDate.of(2026, 10, 10),
                        "RECEITA",
                        "5000.00"
                );

        FinancialTransaction despesaOutubro
                = criarLancamento(
                        LocalDate.of(2026, 10, 15),
                        "DESPESA",
                        "7000.00"
                );

        LocalDate menorData
                = LocalDate.of(2026, 9, 10);

        LocalDate maiorData
                = LocalDate.of(2026, 10, 15);

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
                List.of(
                        receitaSetembro,
                        despesaSetembro,
                        receitaOutubro,
                        despesaOutubro
                )
        );

        /*
         * "todos" representa todo o período existente
         * nos dados do cliente.
         *
         * Neste teste existem apenas setembro e outubro.
         */
        var resultado
                = service.obterFluxoCaixa("todos");

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                "Set/2026",
                resultado.get(0).mes()
        );

        assertEquals(
                "Out/2026",
                resultado.get(1).mes()
        );

        /*
         * SETEMBRO
         */
        assertValor(
                "0",
                resultado.get(0).saldoInicial()
        );

        assertValor(
                "10000.00",
                resultado.get(0).entradas()
        );

        assertValor(
                "4000.00",
                resultado.get(0).saidas()
        );

        assertValor(
                "6000.00",
                resultado.get(0).saldoFinal()
        );

        /*
         * OUTUBRO
         */
        assertValor(
                "6000.00",
                resultado.get(1).saldoInicial()
        );

        assertValor(
                "5000.00",
                resultado.get(1).entradas()
        );

        assertValor(
                "7000.00",
                resultado.get(1).saidas()
        );

        assertValor(
                "4000.00",
                resultado.get(1).saldoFinal()
        );
    }

    @Test
    void deveRetornarListaVaziaNoPeriodoCompletoSemRegistros() {

        when(
                financialTransactionRepository
                        .findMenorDataByClienteId(
                                CLIENTE_ID
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                financialTransactionRepository
                        .findMaiorDataByClienteId(
                                CLIENTE_ID
                        )
        ).thenReturn(
                Optional.empty()
        );

        var resultado
                = service.obterFluxoCaixa("todos");

        assertEquals(
                0,
                resultado.size()
        );
    }

    private FinancialTransaction criarLancamento(
            LocalDate data,
            String tipo,
            String valor
    ) {

        FinancialTransaction lancamento
                = new FinancialTransaction();

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
