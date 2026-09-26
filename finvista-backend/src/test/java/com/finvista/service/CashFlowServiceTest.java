package com.finvista.service;

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

class CashFlowServiceTest {

    private FinancialTransactionRepository financialTransactionRepository;

    private CashFlowService service;

    @BeforeEach
    void configurar() {

        financialTransactionRepository
                = mock(FinancialTransactionRepository.class);

        FinancialTransactionAggregationService transactionAggregationService
                = new FinancialTransactionAggregationService(
                        financialTransactionRepository
                );

        service
                = new CashFlowService(
                        transactionAggregationService
                );
    }

    @Test
    void deveCalcularFluxoCaixaPartindoDeZero() {

       
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
        ).thenReturn(
                List.of(
                        receitaSetembro,
                        despesaSetembro,
                        receitaOutubro,
                        despesaOutubro
                )
        );

        var resultado
                = service.obterFluxoCaixa();

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


        assertEquals(
                0,
                resultado.get(0)
                        .saldoInicial()
                        .compareTo(BigDecimal.ZERO)
        );

        assertEquals(
                0,
                resultado.get(0)
                        .entradas()
                        .compareTo(
                                new BigDecimal("10000.00")
                        )
        );

        assertEquals(
                0,
                resultado.get(0)
                        .saidas()
                        .compareTo(
                                new BigDecimal("4000.00")
                        )
        );

        assertEquals(
                0,
                resultado.get(0)
                        .saldoFinal()
                        .compareTo(
                                new BigDecimal("6000.00")
                        )
        );

        assertEquals(
                0,
                resultado.get(1)
                        .saldoInicial()
                        .compareTo(
                                new BigDecimal("6000.00")
                        )
        );

        assertEquals(
                0,
                resultado.get(1)
                        .entradas()
                        .compareTo(
                                new BigDecimal("5000.00")
                        )
        );

        assertEquals(
                0,
                resultado.get(1)
                        .saidas()
                        .compareTo(
                                new BigDecimal("7000.00")
                        )
        );

        assertEquals(
                0,
                resultado.get(1)
                        .saldoFinal()
                        .compareTo(
                                new BigDecimal("4000.00")
                        )
        );
    }

    @Test
    void deveRetornarListaVaziaSemRegistros() {

        when(
                financialTransactionRepository
                        .findMenorData()
        ).thenReturn(
                Optional.empty()
        );

        when(
                financialTransactionRepository
                        .findMaiorData()
        ).thenReturn(
                Optional.empty()
        );

        var resultado
                = service.obterFluxoCaixa();

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
}
