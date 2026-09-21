package com.finvista.service;


import com.finvista.dto.SpendingGoalResponse;
import com.finvista.model.FinancialTransaction;
import com.finvista.model.SpendingGoal;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.repository.SpendingGoalRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class SpendingGoalServiceTest {

    private SpendingGoalRepository spendingGoalRepository;

    private FinancialTransactionRepository
            financialTransactionRepository;

    private FinancialCalculationService
            calculationService;

    private SpendingGoalService spendingGoalService;

    @BeforeEach
    void configurar() {

        spendingGoalRepository =
                Mockito.mock(
                        SpendingGoalRepository.class
                );

        financialTransactionRepository =
                Mockito.mock(
                        FinancialTransactionRepository.class
                );

        calculationService =
                new FinancialCalculationService();

        spendingGoalService =
                new SpendingGoalService(
                        spendingGoalRepository,
                        financialTransactionRepository,
                        calculationService
                );
    }

    @Test
    void deveRetornarStatusNormal() {

        SpendingGoal meta =
                criarMetaMensal(
                        new BigDecimal("100000.00"),
                        80
                );

        configurarBuscaDaMeta(
                meta,
                new BigDecimal("48101.50")
        );

       SpendingGoalResponse resultado = 
                spendingGoalService
                        .buscarSituacao(1L);

        assertEquals(
                new BigDecimal("48101.50"),
                resultado.gastoAtual()
        );

        assertEquals(
                new BigDecimal("48.10"),
                resultado.percentualUtilizado()
        );

        assertEquals(
                new BigDecimal("51898.50"),
                resultado.saldoMeta()
        );

        assertEquals(
                "NORMAL",
                resultado.status()
        );
    }

    @Test
    void deveRetornarStatusAlerta() {

        SpendingGoal meta =
                criarMetaMensal(
                        new BigDecimal("60000.00"),
                        80
                );

        configurarBuscaDaMeta(
                meta,
                new BigDecimal("48101.50")
        );

        SpendingGoalResponse resultado =
                spendingGoalService
                        .buscarSituacao(1L);

        assertEquals(
                new BigDecimal("80.17"),
                resultado.percentualUtilizado()
        );

        assertEquals(
                new BigDecimal("11898.50"),
                resultado.saldoMeta()
        );

        assertEquals(
                "ALERTA",
                resultado.status()
        );
    }

    @Test
    void deveRetornarStatusExcedida() {

        SpendingGoal meta =
                criarMetaMensal(
                        new BigDecimal("40000.00"),
                        80
                );

        configurarBuscaDaMeta(
                meta,
                new BigDecimal("48101.50")
        );

        SpendingGoalResponse resultado =
                spendingGoalService
                        .buscarSituacao(1L);

        assertEquals(
                new BigDecimal("120.25"),
                resultado.percentualUtilizado()
        );

        assertEquals(
                new BigDecimal("-8101.50"),
                resultado.saldoMeta()
        );

        assertEquals(
                "EXCEDIDA",
                resultado.status()
        );
    }

    @Test
    void deveSomarDespesasDeMetaSemestral() {

        SpendingGoal meta =
                new SpendingGoal(
                        "SEMESTRAL",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("60000.00"),
                        80
                );

        configurarBuscaDaMeta(
                meta,
                new BigDecimal("48101.50"),
                new BigDecimal("1570.00")
        );

        SpendingGoalResponse resultado =
                spendingGoalService
                        .buscarSituacao(1L);

        assertEquals(
                new BigDecimal("49671.50"),
                resultado.gastoAtual()
        );

        assertEquals(
                new BigDecimal("82.79"),
                resultado.percentualUtilizado()
        );

        assertEquals(
                new BigDecimal("10328.50"),
                resultado.saldoMeta()
        );

        assertEquals(
                "ALERTA",
                resultado.status()
        );
    }

    @Test
    void deveRejeitarMetaMensalComPeriodoInvalido() {

        SpendingGoal meta =
                new SpendingGoal(
                        "MENSAL",
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 30),
                        new BigDecimal("60000.00"),
                        80
                );

        IllegalArgumentException excecao =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> spendingGoalService.salvar(meta)
                );

        assertEquals(
                "Meta MENSAL deve compreender um mês completo.",
                excecao.getMessage()
        );
    }

    @Test
    void deveRejeitarMetaSemestralComPeriodoInvalido() {

        SpendingGoal meta =
                new SpendingGoal(
                        "SEMESTRAL",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("60000.00"),
                        80
                );

        IllegalArgumentException excecao =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> spendingGoalService.salvar(meta)
                );

        assertEquals(
                "Meta SEMESTRAL deve compreender um semestre completo.",
                excecao.getMessage()
        );
    }

    @Test
    void deveRejeitarPercentualAlertaInvalido() {

        SpendingGoal meta =
                criarMetaMensal(
                        new BigDecimal("60000.00"),
                        101
                );

        IllegalArgumentException excecao =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> spendingGoalService.salvar(meta)
                );

        assertEquals(
                "Percentual de alerta deve estar entre 1 e 100.",
                excecao.getMessage()
        );
    }

    @Test
    void deveRejeitarValorLimiteZero() {

        SpendingGoal meta =
                criarMetaMensal(
                        BigDecimal.ZERO,
                        80
                );

        IllegalArgumentException excecao =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> spendingGoalService.salvar(meta)
                );

        assertEquals(
                "Valor limite deve ser maior que zero.",
                excecao.getMessage()
        );
    }

    private SpendingGoal criarMetaMensal(
            BigDecimal valorLimite,
            Integer percentualAlerta
    ) {
        return new SpendingGoal(
                "MENSAL",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                valorLimite,
                percentualAlerta
        );
    }

    private void configurarBuscaDaMeta(
            SpendingGoal meta,
            BigDecimal... valoresDespesas
    ) {
        when(
                spendingGoalRepository.findById(1L)
        ).thenReturn(
                Optional.of(meta)
        );

        List<FinancialTransaction> despesas =
                java.util.Arrays.stream(valoresDespesas)
                        .map(this::criarDespesa)
                        .toList();

        when(
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                "DESPESA",
                                meta.getDataInicio(),
                                meta.getDataFim()
                        )
        ).thenReturn(despesas);
    }

    private FinancialTransaction criarDespesa(
            BigDecimal valor
    ) {
        FinancialTransaction despesa =
                new FinancialTransaction();

        despesa.setValor(valor);

        return despesa;
    }
}
