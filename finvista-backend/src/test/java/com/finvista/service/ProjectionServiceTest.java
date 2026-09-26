package com.finvista.service;

import com.finvista.dto.ProjectionResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ProjectionServiceTest {

    private FinancialTransactionRepository financialTransactionRepository;

    private ProjectionService projectionService;

    @BeforeEach
    void configurar() {

        financialTransactionRepository
                = Mockito.mock(
                        FinancialTransactionRepository.class
                );

        FinancialTransactionAggregationService aggregationService
                = new FinancialTransactionAggregationService(
                        financialTransactionRepository
                );

        FinancialCalculationService calculationService
                = new FinancialCalculationService();

        Clock clock
                = Clock.fixed(
                        Instant.parse(
                                "2026-09-20T12:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        projectionService
                = new ProjectionService(
                        aggregationService,
                        calculationService,
                        clock
                );
    }

    @Test
    void deveCalcularProjecaoComDadosFinanceiros() {

        List<FinancialTransaction> lancamentos
                = List.of(
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        9,
                                        18
                                ),
                                "RECEITA",
                                "247500.75"
                        ),
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        9,
                                        20
                                ),
                                "DESPESA",
                                "48101.50"
                        ),
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        10,
                                        10
                                ),
                                "RECEITA",
                                "22560.25"
                        ),
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        10,
                                        27
                                ),
                                "DESPESA",
                                "1570.00"
                        )
                );

        when(
                financialTransactionRepository
                        .findByDataBetweenOrderByDataAsc(
                                any(),
                                any()
                        )
        ).thenReturn(lancamentos);

        List<ProjectionResponse> resultado
                = projectionService.obterProjecao();

        assertEquals(
                6,
                resultado.size()
        );

        ProjectionResponse setembro
                = resultado.get(0);

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
                new BigDecimal("48101.50"),
                setembro.despesa()
        );

        assertEquals(
                new BigDecimal("199399.25"),
                setembro.resultado()
        );

        assertEquals(
                new BigDecimal("199399.25"),
                setembro.saldo()
        );

        ProjectionResponse outubro
                = resultado.get(1);

        assertEquals(
                new BigDecimal("22560.25"),
                outubro.receitaRealizada()
        );

        assertEquals(
                BigDecimal.ZERO,
                outubro.receitaProjetada()
        );

        assertEquals(
                new BigDecimal("1570.00"),
                outubro.despesa()
        );

        assertEquals(
                new BigDecimal("20990.25"),
                outubro.resultado()
        );

        assertEquals(
                new BigDecimal("220389.50"),
                outubro.saldo()
        );

        ProjectionResponse novembro
                = resultado.get(2);

        assertEquals(
                BigDecimal.ZERO,
                novembro.receitaRealizada()
        );

        assertEquals(
                BigDecimal.ZERO,
                novembro.receitaProjetada()
        );

        assertEquals(
                BigDecimal.ZERO,
                novembro.receita()
        );

        assertEquals(
                BigDecimal.ZERO,
                novembro.despesa()
        );

        assertEquals(
                new BigDecimal("220389.50"),
                novembro.saldo()
        );
    }

    @Test
    void deveManterSaldoAcumuladoNosMesesSemMovimento() {

        List<FinancialTransaction> lancamentos
                = List.of(
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        9,
                                        18
                                ),
                                "RECEITA",
                                "10000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        9,
                                        20
                                ),
                                "DESPESA",
                                "4000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        10,
                                        10
                                ),
                                "RECEITA",
                                "5000.00"
                        ),
                        criarLancamento(
                                LocalDate.of(
                                        2026,
                                        10,
                                        27
                                ),
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

        List<ProjectionResponse> resultado
                = projectionService.obterProjecao();

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

        FinancialTransaction lancamento
                = new FinancialTransaction();

        lancamento.setData(data);
        lancamento.setTipo(tipo);
        lancamento.setValor(
                new BigDecimal(valor)
        );

        return lancamento;
    }
}
