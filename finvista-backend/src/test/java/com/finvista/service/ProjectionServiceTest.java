package com.finvista.service;

import com.finvista.model.Budget;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.BudgetRepository;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.dto.ProjectionResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ProjectionServiceTest {

    private FinancialTransactionRepository
            financialTransactionRepository;

    private BudgetRepository
            budgetRepository;

    private ProjectionService
            projectionService;

    @BeforeEach
void configurar() {

    financialTransactionRepository =
            Mockito.mock(
                    FinancialTransactionRepository.class
            );

    budgetRepository =
            Mockito.mock(
                    BudgetRepository.class
            );

    FinancialTransactionAggregationService aggregationService =
            new FinancialTransactionAggregationService(
                    financialTransactionRepository
            );

    FinancialCalculationService calculationService =
            new FinancialCalculationService();

    BudgetProjectionService budgetProjectionService =
            new BudgetProjectionService(
                    budgetRepository,
                    calculationService
            );

    Clock clock =
            Clock.fixed(
                    Instant.parse("2026-09-20T12:00:00Z"),
                    ZoneOffset.UTC
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
    void deveCalcularProjecaoComDadosReaisEOrcamentos() {

        List<FinancialTransaction> lancamentos =
                List.of(
                        criarLancamento(
                                LocalDate.of(2026, 9, 18),
                                "RECEITA",
                                "247500.75"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 9, 20),
                                "DESPESA",
                                "48101.50"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 10, 10),
                                "RECEITA",
                                "22560.25"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 10, 27),
                                "DESPESA",
                                "1570.00"
                        )
                );

        List<Budget> orcamentos =
                List.of(
                        criarOrcamento(
                                "Madeireira Norte",
                                "120000.00",
                                70,
                                LocalDate.of(
                                        2026,
                                        11,
                                        15
                                )
                        ),
                        criarOrcamento(
                                "Construtora Acre",
                                "85000.00",
                                80,
                                LocalDate.of(
                                        2026,
                                        12,
                                        10
                                )
                        ),
                        criarOrcamento(
                                "Grupo Florestal",
                                "150000.00",
                                50,
                                LocalDate.of(
                                        2026,
                                        12,
                                        20
                                )
                        ),
                        criarOrcamento(
                                "Indústria Amazônia",
                                "65000.00",
                                30,
                                LocalDate.of(
                                        2027,
                                        1,
                                        15
                                )
                        ),
                        criarOrcamento(
                                "Madeiras Brasil",
                                "95000.00",
                                90,
                                LocalDate.of(
                                        2027,
                                        2,
                                        10
                                )
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                any(),
                                any()
                        )
        ).thenReturn(lancamentos);

        when(
                budgetRepository
                        .findByDataPrevisaoFechamentoBetweenOrderByDataPrevisaoFechamentoAsc(
                                any(),
                                any()
                        )
        ).thenReturn(orcamentos);

        List<ProjectionResponse> resultado =
        projectionService.obterProjecao();

        assertEquals(
                6,
                resultado.size()
        );

      ProjectionResponse setembro = resultado.get(0);

        assertEquals(
                new BigDecimal("247500.75"),
                setembro.receitaRealizada()
        );

        assertEquals(
                BigDecimal.ZERO,
                setembro.receitaProjetada()
        );

        assertEquals(
                new BigDecimal("247500.75"),
                setembro.receita()
        );

        assertEquals(
                new BigDecimal("199399.25"),
                setembro.resultado()
        );

        assertEquals(
                new BigDecimal("199399.25"),
                setembro.saldo()
        );

       ProjectionResponse outubro = resultado.get(1);

        assertEquals(
                new BigDecimal("22560.25"),
                outubro.receitaRealizada()
        );

        assertEquals(
                BigDecimal.ZERO,
                outubro.receitaProjetada()
        );

        assertEquals(
                new BigDecimal("220389.50"),
                outubro.saldo()
        );

       ProjectionResponse novembro = resultado.get(2);

        assertEquals(
                BigDecimal.ZERO,
                novembro.receitaRealizada()
        );

        assertEquals(
                new BigDecimal("84000.00"),
                novembro.receitaProjetada()
        );

        assertEquals(
                new BigDecimal("84000.00"),
                novembro.receita()
        );

        assertEquals(
                new BigDecimal("84000.00"),
                novembro.resultado()
        );

        assertEquals(
                new BigDecimal("100.00"),
                novembro.margem()
        );

        assertEquals(
                new BigDecimal("304389.50"),
                novembro.saldo()
        );

       ProjectionResponse dezembro = resultado.get(3);

        assertEquals(
                new BigDecimal("143000.00"),
                dezembro.receitaProjetada()
        );

        assertEquals(
                new BigDecimal("447389.50"),
                dezembro.saldo()
        );

        ProjectionResponse janeiro = resultado.get(4);

        assertEquals(
                new BigDecimal("19500.00"),
                janeiro.receitaProjetada()
        );

        assertEquals(
                new BigDecimal("466889.50"),
                janeiro.saldo()
        );

        ProjectionResponse fevereiro = resultado.get(5);

        assertEquals(
                new BigDecimal("85500.00"),
                fevereiro.receitaProjetada()
        );

        assertEquals(
                new BigDecimal("552389.50"),
                fevereiro.saldo()
        );
    }

    @Test
    void deveManterComportamentoSemOrcamentos() {

        List<FinancialTransaction> lancamentos =
                List.of(
                        criarLancamento(
                                LocalDate.of(2026, 9, 18),
                                "RECEITA",
                                "10000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 9, 20),
                                "DESPESA",
                                "4000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 10, 10),
                                "RECEITA",
                                "5000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(2026, 10, 27),
                                "DESPESA",
                                "7000.00"
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                any(),
                                any()
                        )
        ).thenReturn(lancamentos);

        when(
                budgetRepository
                        .findByDataPrevisaoFechamentoBetweenOrderByDataPrevisaoFechamentoAsc(
                                any(),
                                any()
                        )
        ).thenReturn(List.of());

        List<ProjectionResponse> resultado =
                projectionService.obterProjecao();

        assertEquals(
                6,
                resultado.size()
        );

        assertEquals(
                new BigDecimal("6000.00"),
                resultado.get(0).saldo()
        );

        assertEquals(
                new BigDecimal("-2000.00"),
                resultado.get(1).resultado()
        );

        assertEquals(
                new BigDecimal("4000.00"),
                resultado.get(1).saldo()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(2).receitaRealizada()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(2).receitaProjetada()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(2).receita()
        );

        assertEquals(
                BigDecimal.ZERO,
                resultado.get(2).despesa()
        );

        assertEquals(
                new BigDecimal("4000.00"),
                resultado.get(2).saldo()
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

    private Budget criarOrcamento(
            String cliente,
            String valor,
            Integer probabilidade,
            LocalDate dataPrevisaoFechamento
    ) {

        return new Budget(
                cliente,
                new BigDecimal(valor),
                "Em negociação",
                probabilidade,
                dataPrevisaoFechamento
        );
    }
}
