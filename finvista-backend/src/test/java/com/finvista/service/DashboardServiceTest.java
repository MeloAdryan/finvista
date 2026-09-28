package com.finvista.service;

import com.finvista.dto.DashboardResponse;
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
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private FinancialTransactionRepository
            financialTransactionRepository;

    private ClienteContextService
            clienteContextService;

    private DashboardService
            dashboardService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository =
                Mockito.mock(
                        FinancialTransactionRepository.class
                );

        clienteContextService =
                Mockito.mock(
                        ClienteContextService.class
                );

        /*
         * Os testes deste serviço representam
         * a visão administrativa/global.
         */
        when(
                clienteContextService.isAdmin()
        ).thenReturn(true);

        FinancialTransactionAggregationService
                transactionAggregationService =
                new FinancialTransactionAggregationService(
                        financialTransactionRepository,
                        clienteContextService
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
    void deveMontarDashboardComComparacaoAoMesAnterior() {

        YearMonth mesAtual =
                YearMonth.now();

        YearMonth mesAnterior =
                mesAtual.minusMonths(1);

        LocalDate dataInicial =
                mesAnterior.atDay(1);

        LocalDate dataFinal =
                mesAtual.atEndOfMonth();

        List<FinancialTransaction> lancamentos =
                List.of(

                        /*
                         * Mês anterior
                         */
                        criarLancamento(
                                mesAnterior.atDay(5),
                                "RECEITA",
                                "100000.00"
                        ),

                        criarLancamento(
                                mesAnterior.atDay(10),
                                "DESPESA",
                                "60000.00"
                        ),

                        /*
                         * Mês atual
                         */
                        criarLancamento(
                                mesAtual.atDay(5),
                                "RECEITA",
                                "150000.00"
                        ),

                        criarLancamento(
                                mesAtual.atDay(10),
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

        /*
         * =====================================================
         * MÊS ATUAL
         * =====================================================
         */

        assertValor(
                "150000.00",
                resposta.receita()
        );

        assertValor(
                "92000.00",
                resposta.despesa()
        );

        assertValor(
                "58000.00",
                resposta.resultado()
        );

        assertValor(
                "38.67",
                resposta.margem()
        );

        /*
         * =====================================================
         * MÊS ANTERIOR
         * =====================================================
         */

        assertValor(
                "100000.00",
                resposta.receitaMesAnterior()
        );

        assertValor(
                "60000.00",
                resposta.despesaMesAnterior()
        );

        assertValor(
                "40000.00",
                resposta.resultadoMesAnterior()
        );

        assertValor(
                "40.00",
                resposta.margemMesAnterior()
        );

        /*
         * =====================================================
         * VARIAÇÕES
         * =====================================================
         */

        /*
         * Receita:
         *
         * (150000 - 100000) / 100000 * 100
         *
         * = 50%
         */
        assertValor(
                "50.00",
                resposta.variacaoReceita()
        );

        /*
         * Despesa:
         *
         * (92000 - 60000) / 60000 * 100
         *
         * = 53,33%
         */
        assertValor(
                "53.33",
                resposta.variacaoDespesa()
        );

        /*
         * Resultado:
         *
         * (58000 - 40000) / 40000 * 100
         *
         * = 45%
         */
        assertValor(
                "45.00",
                resposta.variacaoResultado()
        );
    }

    @Test
    void deveRetornarDashboardZeradoQuandoNaoExistiremLancamentos() {

        YearMonth mesAtual =
                YearMonth.now();

        YearMonth mesAnterior =
                mesAtual.minusMonths(1);

        LocalDate dataInicial =
                mesAnterior.atDay(1);

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

        assertValor(
                "0",
                resposta.receita()
        );

        assertValor(
                "0",
                resposta.despesa()
        );

        assertValor(
                "0",
                resposta.resultado()
        );

        assertValor(
                "0",
                resposta.margem()
        );

        assertValor(
                "0",
                resposta.receitaMesAnterior()
        );

        assertValor(
                "0",
                resposta.despesaMesAnterior()
        );

        assertValor(
                "0",
                resposta.resultadoMesAnterior()
        );

        assertValor(
                "0",
                resposta.margemMesAnterior()
        );

        assertValor(
                "0",
                resposta.variacaoReceita()
        );

        assertValor(
                "0",
                resposta.variacaoDespesa()
        );

        assertValor(
                "0",
                resposta.variacaoResultado()
        );
    }

    @Test
    void deveCalcularVariacaoZeroQuandoMesAnteriorNaoPossuirValores() {

        YearMonth mesAtual =
                YearMonth.now();

        YearMonth mesAnterior =
                mesAtual.minusMonths(1);

        LocalDate dataInicial =
                mesAnterior.atDay(1);

        LocalDate dataFinal =
                mesAtual.atEndOfMonth();

        List<FinancialTransaction> lancamentos =
                List.of(
                        criarLancamento(
                                mesAtual.atDay(5),
                                "RECEITA",
                                "50000.00"
                        ),

                        criarLancamento(
                                mesAtual.atDay(10),
                                "DESPESA",
                                "20000.00"
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

        assertValor(
                "50000.00",
                resposta.receita()
        );

        assertValor(
                "20000.00",
                resposta.despesa()
        );

        assertValor(
                "30000.00",
                resposta.resultado()
        );

        assertValor(
                "60.00",
                resposta.margem()
        );

        assertValor(
                "0",
                resposta.receitaMesAnterior()
        );

        assertValor(
                "0",
                resposta.despesaMesAnterior()
        );

        assertValor(
                "0",
                resposta.resultadoMesAnterior()
        );

        /*
         * Como não existe base de comparação,
         * não tentamos produzir uma porcentagem
         * infinita ou artificial.
         */
        assertValor(
                "0",
                resposta.variacaoReceita()
        );

        assertValor(
                "0",
                resposta.variacaoDespesa()
        );

        assertValor(
                "0",
                resposta.variacaoResultado()
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