package com.finvista.integration;

import com.finvista.dto.ProjectionResponse;
import com.finvista.model.Budget;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.BudgetRepository;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.service.BudgetProjectionService;
import com.finvista.service.FinancialCalculationService;
import com.finvista.service.FinancialTransactionAggregationService;
import com.finvista.service.ProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialEngineIntegrationTest {

    private FinancialTransactionRepository
            financialTransactionRepository;

    private BudgetRepository budgetRepository;

    private ProjectionService projectionService;

    @BeforeEach
    void setUp() {

        financialTransactionRepository =
                mock(FinancialTransactionRepository.class);

        budgetRepository =
                mock(BudgetRepository.class);

        FinancialCalculationService calculationService =
                new FinancialCalculationService();

        FinancialTransactionAggregationService aggregationService =
                new FinancialTransactionAggregationService(
                        financialTransactionRepository
                );

        BudgetProjectionService budgetProjectionService =
                new BudgetProjectionService(
                        budgetRepository,
                        calculationService
                );

        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2026-09-20T12:00:00Z"
                        ),
                        ZoneId.of("UTC")
                );

        projectionService =
                new ProjectionService(
                        aggregationService,
                        budgetProjectionService,
                        calculationService,
                        clock
                );
    }

    @Test
    void deveIntegrarTransacoesOrcamentosEProjecaoDeSeisMeses() {

        List<FinancialTransaction> transacoes =
                List.of(
                        novaTransacao(
                                LocalDate.of(2026, 9, 5),
                                "Venda Setembro",
                                "RECEITA",
                                "100000.00"
                        ),
                        novaTransacao(
                                LocalDate.of(2026, 9, 10),
                                "Despesa Setembro",
                                "DESPESA",
                                "40000.00"
                        ),
                        novaTransacao(
                                LocalDate.of(2026, 10, 5),
                                "Venda Outubro",
                                "RECEITA",
                                "30000.00"
                        ),
                        novaTransacao(
                                LocalDate.of(2026, 10, 10),
                                "Despesa Outubro",
                                "DESPESA",
                                "10000.00"
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                any(LocalDate.class),
                                any(LocalDate.class)
                        )
        ).thenReturn(transacoes);

        List<Budget> orcamentos =
                List.of(
                        new Budget(
                                "Cliente Novembro",
                                new BigDecimal("80000.00"),
                                "ABERTO",
                                50,
                                LocalDate.of(2026, 11, 15)
                        ),
                        new Budget(
                                "Cliente Dezembro",
                                new BigDecimal("100000.00"),
                                "ABERTO",
                                70,
                                LocalDate.of(2026, 12, 10)
                        )
                );

        when(
                budgetRepository
                        .findByDataPrevisaoFechamentoBetweenOrderByDataPrevisaoFechamentoAsc(
                                any(LocalDate.class),
                                any(LocalDate.class)
                        )
        ).thenReturn(orcamentos);

        List<ProjectionResponse> projecao =
                projectionService.obterProjecao();

        assertEquals(
                6,
                projecao.size()
        );

        ProjectionResponse setembro =
                projecao.get(0);

        assertEquals(
                "2026-09",
                setembro.periodo()
        );

        assertValor(
                "100000.00",
                setembro.receitaRealizada()
        );

        assertValor(
                "0",
                setembro.receitaProjetada()
        );

        assertValor(
                "100000.00",
                setembro.receita()
        );

        assertValor(
                "40000.00",
                setembro.despesa()
        );

        assertValor(
                "60000.00",
                setembro.resultado()
        );

        assertValor(
                "60.00",
                setembro.margem()
        );

        assertValor(
                "60000.00",
                setembro.saldo()
        );

        ProjectionResponse outubro =
                projecao.get(1);

        assertEquals(
                "2026-10",
                outubro.periodo()
        );

        assertValor(
                "30000.00",
                outubro.receitaRealizada()
        );

        assertValor(
                "0",
                outubro.receitaProjetada()
        );

        assertValor(
                "20000.00",
                outubro.resultado()
        );

        assertValor(
                "80000.00",
                outubro.saldo()
        );

        ProjectionResponse novembro =
                projecao.get(2);

        assertEquals(
                "2026-11",
                novembro.periodo()
        );

        assertValor(
                "0",
                novembro.receitaRealizada()
        );

        assertValor(
                "40000.00",
                novembro.receitaProjetada()
        );

        assertValor(
                "40000.00",
                novembro.receita()
        );

        assertValor(
                "40000.00",
                novembro.resultado()
        );

        assertValor(
                "100.00",
                novembro.margem()
        );

        assertValor(
                "120000.00",
                novembro.saldo()
        );

        ProjectionResponse dezembro =
                projecao.get(3);

        assertEquals(
                "2026-12",
                dezembro.periodo()
        );

        assertValor(
                "70000.00",
                dezembro.receitaProjetada()
        );

        assertValor(
                "70000.00",
                dezembro.resultado()
        );

        assertValor(
                "190000.00",
                dezembro.saldo()
        );

        ProjectionResponse janeiro =
                projecao.get(4);

        assertEquals(
                "2027-01",
                janeiro.periodo()
        );

        assertValor(
                "0",
                janeiro.receita()
        );

        assertValor(
                "0",
                janeiro.resultado()
        );

        assertValor(
                "190000.00",
                janeiro.saldo()
        );

        ProjectionResponse fevereiro =
                projecao.get(5);

        assertEquals(
                "2027-02",
                fevereiro.periodo()
        );

        assertValor(
                "0",
                fevereiro.receita()
        );

        assertValor(
                "0",
                fevereiro.resultado()
        );

        assertValor(
                "190000.00",
                fevereiro.saldo()
        );
    }

    private FinancialTransaction novaTransacao(
            LocalDate data,
            String descricao,
            String tipo,
            String valor
    ) {
        return new FinancialTransaction(
                data,
                descricao,
                tipo,
                new BigDecimal(valor),
                "TESTE",
                "TESTE",
                "TESTE_INTEGRACAO",
                descricao
        );
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