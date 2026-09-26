package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.finvista.dto.DashboardResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;

class DashboardServiceTest {

    private FinancialTransactionRepository
            financialTransactionRepository;

    private DashboardService
            dashboardService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository =
                Mockito.mock(
                        FinancialTransactionRepository.class
                );

        FinancialTransactionAggregationService
                transactionAggregationService =
                new FinancialTransactionAggregationService(
                        financialTransactionRepository
                );

        FinancialCalculationService calculationService =
                new FinancialCalculationService();

        dashboardService =
                new DashboardService(
                        transactionAggregationService,
                        calculationService
                );
    }

    @Test
    void deveMontarDashboardComLancamentosDoMesAtual() {

        YearMonth mesAtual =
                YearMonth.now();

        LocalDate dataInicial =
                mesAtual.atDay(1);

        LocalDate dataFinal =
                mesAtual.atEndOfMonth();

        List<FinancialTransaction> lancamentos =
                List.of(
                        criarLancamento(
                                LocalDate.of(
                                        mesAtual.getYear(),
                                        mesAtual.getMonth(),
                                        5
                                ),
                                "RECEITA",
                                "150000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(
                                        mesAtual.getYear(),
                                        mesAtual.getMonth(),
                                        10
                                ),
                                "DESPESA",
                                "92000.00"
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                dataInicial,
                                dataFinal
                        )
        ).thenReturn(lancamentos);

        DashboardResponse resposta =
                dashboardService.obterDashboard();

        assertEquals(
                0,
                resposta.receita()
                        .compareTo(
                                new BigDecimal("150000.00")
                        )
        );

        assertEquals(
                0,
                resposta.despesa()
                        .compareTo(
                                new BigDecimal("92000.00")
                        )
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
    void deveRetornarDashboardZeradoQuandoNaoExistiremLancamentosNoMesAtual() {

        YearMonth mesAtual =
                YearMonth.now();

        LocalDate dataInicial =
                mesAtual.atDay(1);

        LocalDate dataFinal =
                mesAtual.atEndOfMonth();

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                dataInicial,
                                dataFinal
                        )
        ).thenReturn(
                List.of()
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