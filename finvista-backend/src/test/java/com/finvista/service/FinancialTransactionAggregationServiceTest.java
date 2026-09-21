package com.finvista.service;

import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class FinancialTransactionAggregationServiceTest {

    private FinancialTransactionRepository
            financialTransactionRepository;

    private FinancialTransactionAggregationService
            aggregationService;

    @BeforeEach
    void configurar() {

        financialTransactionRepository =
                Mockito.mock(
                        FinancialTransactionRepository.class
                );

        aggregationService =
                new FinancialTransactionAggregationService(
                        financialTransactionRepository
                );
    }

    @Test
    void deveAgruparReceitasEDespesasPorMes() {

        LocalDate inicio =
                LocalDate.of(2026, 9, 1);

        LocalDate fim =
                LocalDate.of(2026, 10, 31);

        List<FinancialTransaction> lancamentos =
                List.of(
                        criarLancamento(
                                LocalDate.of(2026, 9, 5),
                                "RECEITA",
                                "10000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 9, 10),
                                "DESPESA",
                                "3000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 9, 20),
                                "DESPESA",
                                "2000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 10, 3),
                                "RECEITA",
                                "15000.00"
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                inicio,
                                fim
                        )
        ).thenReturn(lancamentos);

        List<
                FinancialTransactionAggregationService
                        .MonthlyFinancialSummary
        > resultado =
                aggregationService.obterResumoMensal(
                        inicio,
                        fim
                );

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                YearMonth.of(2026, 9),
                resultado.get(0).periodo()
        );

        assertEquals(
                new BigDecimal("10000.00"),
                resultado.get(0).receita()
        );

        assertEquals(
                new BigDecimal("5000.00"),
                resultado.get(0).despesa()
        );

        assertEquals(
                YearMonth.of(2026, 10),
                resultado.get(1).periodo()
        );

        assertEquals(
                new BigDecimal("15000.00"),
                resultado.get(1).receita()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(1).despesa()
        );
    }

    @Test
    void deveManterMesSemMovimentacaoComValoresZero() {

        LocalDate inicio =
                LocalDate.of(2026, 9, 1);

        LocalDate fim =
                LocalDate.of(2026, 11, 30);

        List<FinancialTransaction> lancamentos =
                List.of(
                        criarLancamento(
                                LocalDate.of(2026, 9, 10),
                                "RECEITA",
                                "10000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 11, 10),
                                "DESPESA",
                                "4000.00"
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                inicio,
                                fim
                        )
        ).thenReturn(lancamentos);

        List<
                FinancialTransactionAggregationService
                        .MonthlyFinancialSummary
        > resultado =
                aggregationService.obterResumoMensal(
                        inicio,
                        fim
                );

        assertEquals(
                3,
                resultado.size()
        );

        assertEquals(
                YearMonth.of(2026, 10),
                resultado.get(1).periodo()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(1).receita()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(1).despesa()
        );
    }

    @Test
    void deveIgnorarTipoDesconhecido() {

        LocalDate inicio =
                LocalDate.of(2026, 9, 1);

        LocalDate fim =
                LocalDate.of(2026, 9, 30);

        List<FinancialTransaction> lancamentos =
                List.of(
                        criarLancamento(
                                LocalDate.of(2026, 9, 10),
                                "OUTRO",
                                "9999.00"
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                inicio,
                                fim
                        )
        ).thenReturn(lancamentos);

        var resultado =
                aggregationService.obterResumoMensal(
                        inicio,
                        fim
                );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(0).receita()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(0).despesa()
        );
    }

    @Test
    void deveRejeitarPeriodoInvalido() {

        LocalDate inicio =
                LocalDate.of(2026, 10, 1);

        LocalDate fim =
                LocalDate.of(2026, 9, 30);

        IllegalArgumentException excecao =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> aggregationService
                                .obterResumoMensal(
                                        inicio,
                                        fim
                                )
                );

        assertEquals(
                "Data final não pode ser anterior à data inicial.",
                excecao.getMessage()
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
        lancamento.setTipo(tipo);
        lancamento.setValor(
                new BigDecimal(valor)
        );

        return lancamento;
    }
}