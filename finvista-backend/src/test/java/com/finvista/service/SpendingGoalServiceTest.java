package com.finvista.service;

import com.finvista.dto.SpendingGoalResponse;
import com.finvista.model.Cliente;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpendingGoalServiceTest {

    private static final Long CLIENTE_ID = 1L;

    private SpendingGoalRepository spendingGoalRepository;

    private FinancialTransactionRepository financialTransactionRepository;

    private FinancialCalculationService calculationService;

    private ClienteContextService clienteContextService;

    private Cliente cliente;

    private SpendingGoalService spendingGoalService;

    @BeforeEach
    void configurar() {

        spendingGoalRepository
                = Mockito.mock(
                        SpendingGoalRepository.class
                );

        financialTransactionRepository
                = Mockito.mock(
                        FinancialTransactionRepository.class
                );

        clienteContextService
                = Mockito.mock(
                        ClienteContextService.class
                );

        calculationService
                = new FinancialCalculationService();

        cliente
                = Mockito.mock(Cliente.class);

        when(cliente.getId())
                .thenReturn(CLIENTE_ID);

        when(
                clienteContextService.getClienteAtual()
        ).thenReturn(cliente);

        when(
                clienteContextService.getClienteAtualId()
        ).thenReturn(CLIENTE_ID);

        spendingGoalService
                = new SpendingGoalService(
                        spendingGoalRepository,
                        financialTransactionRepository,
                        calculationService,
                        clienteContextService
                );
    }

    @Test
    void deveRetornarStatusNormal() {

        SpendingGoal meta
                = criarMetaMensal(
                        new BigDecimal("100000.00"),
                        80
                );

        configurarBuscaDaMeta(
                meta,
                new BigDecimal("48101.50")
        );

        SpendingGoalResponse resultado
                = spendingGoalService
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

        SpendingGoal meta
                = criarMetaMensal(
                        new BigDecimal("60000.00"),
                        80
                );

        configurarBuscaDaMeta(
                meta,
                new BigDecimal("48101.50")
        );

        SpendingGoalResponse resultado
                = spendingGoalService
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

        SpendingGoal meta
                = criarMetaMensal(
                        new BigDecimal("40000.00"),
                        80
                );

        configurarBuscaDaMeta(
                meta,
                new BigDecimal("48101.50")
        );

        SpendingGoalResponse resultado
                = spendingGoalService
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

        SpendingGoal meta
                = new SpendingGoal(
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

        SpendingGoalResponse resultado
                = spendingGoalService
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

        SpendingGoal meta
                = new SpendingGoal(
                        "MENSAL",
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 30),
                        new BigDecimal("60000.00"),
                        80
                );

        IllegalArgumentException excecao
                = assertThrows(
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

        SpendingGoal meta
                = new SpendingGoal(
                        "SEMESTRAL",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("60000.00"),
                        80
                );

        IllegalArgumentException excecao
                = assertThrows(
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

        SpendingGoal meta
                = criarMetaMensal(
                        new BigDecimal("60000.00"),
                        101
                );

        IllegalArgumentException excecao
                = assertThrows(
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

        SpendingGoal meta
                = criarMetaMensal(
                        BigDecimal.ZERO,
                        80
                );

        IllegalArgumentException excecao
                = assertThrows(
                        IllegalArgumentException.class,
                        () -> spendingGoalService.salvar(meta)
                );

        assertEquals(
                "Valor limite deve ser maior que zero.",
                excecao.getMessage()
        );
    }

    @Test
    void deveAssociarMetaAoClienteAutenticadoAoSalvar() {

        SpendingGoal meta
                = criarMetaMensal(
                        new BigDecimal("60000.00"),
                        80
                );

        when(
                spendingGoalRepository.save(meta)
        ).thenReturn(meta);

        SpendingGoal resultado
                = spendingGoalService.salvar(meta);

        assertSame(
                cliente,
                meta.getCliente()
        );

        assertSame(
                meta,
                resultado
        );

        verify(
                spendingGoalRepository
        ).save(meta);
    }

    @Test
    void deveBuscarMetaSomenteDoClienteAutenticado() {

        SpendingGoal meta
                = criarMetaMensal(
                        new BigDecimal("60000.00"),
                        80
                );

        meta.setCliente(cliente);

        when(
                spendingGoalRepository
                        .findByIdAndClienteId(
                                10L,
                                CLIENTE_ID
                        )
        ).thenReturn(
                Optional.of(meta)
        );

        SpendingGoal resultado
                = spendingGoalService
                        .buscarPorId(10L);

        assertSame(
                meta,
                resultado
        );

        verify(
                spendingGoalRepository
        ).findByIdAndClienteId(
                10L,
                CLIENTE_ID
        );
    }

    @Test
    void naoDeveEncontrarMetaDeOutroCliente() {

        Long metaId = 99L;

        when(
                spendingGoalRepository
                        .findByIdAndClienteId(
                                metaId,
                                CLIENTE_ID
                        )
        ).thenReturn(
                Optional.empty()
        );

        IllegalArgumentException excecao
                = assertThrows(
                        IllegalArgumentException.class,
                        ()
                        -> spendingGoalService
                                .buscarSituacao(metaId)
                );

        assertEquals(
                "Meta de gastos não encontrada: 99",
                excecao.getMessage()
        );

        verify(
                spendingGoalRepository
        ).findByIdAndClienteId(
                metaId,
                CLIENTE_ID
        );

        verify(
                financialTransactionRepository,
                never()
        ).findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                Mockito.anyLong(),
                Mockito.anyString(),
                Mockito.any(LocalDate.class),
                Mockito.any(LocalDate.class)
        );
    }

    @Test
    void deveListarSomenteMetasDoClienteAutenticado() {

        SpendingGoal primeira
                = criarMetaMensal(
                        new BigDecimal("60000.00"),
                        80
                );

        SpendingGoal segunda
                = criarMetaMensal(
                        new BigDecimal("80000.00"),
                        90
                );

        primeira.setCliente(cliente);
        segunda.setCliente(cliente);

        when(
                spendingGoalRepository
                        .findByClienteIdOrderByDataInicioDesc(
                                CLIENTE_ID
                        )
        ).thenReturn(
                List.of(
                        primeira,
                        segunda
                )
        );

        List<SpendingGoal> resultado
                = spendingGoalService
                        .listarMetas();

        assertEquals(
                2,
                resultado.size()
        );

        verify(
                spendingGoalRepository
        ).findByClienteIdOrderByDataInicioDesc(
                CLIENTE_ID
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

        /*
         * A meta pertence ao cliente autenticado.
         */
        meta.setCliente(cliente);

        when(
                spendingGoalRepository
                        .findByIdAndClienteId(
                                1L,
                                CLIENTE_ID
                        )
        ).thenReturn(
                Optional.of(meta)
        );

        List<FinancialTransaction> despesas
                = java.util.Arrays
                        .stream(valoresDespesas)
                        .map(this::criarDespesa)
                        .toList();

        when(
                financialTransactionRepository
                        .findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(
                                CLIENTE_ID,
                                "DESPESA",
                                meta.getDataInicio(),
                                meta.getDataFim()
                        )
        ).thenReturn(despesas);
    }

    private FinancialTransaction criarDespesa(
            BigDecimal valor
    ) {

        FinancialTransaction despesa
                = new FinancialTransaction();

        despesa.setValor(valor);

        return despesa;
    }
}
