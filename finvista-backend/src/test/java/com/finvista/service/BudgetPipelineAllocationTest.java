package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BudgetPipelineAllocationTest {

    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
    private final FinancialAllocationRepository rates = mock(FinancialAllocationRepository.class);
    private final ClienteContextService context = mock(ClienteContextService.class);
    private final Cliente cliente = mock(Cliente.class);
    private final BudgetPipelineService service = new BudgetPipelineService(budgets, transactions, context, new ExpenseAllocationService(rates));
    private final LocalDate inicio = LocalDate.of(2026, 9, 1), fim = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void configurarCliente() {
        when(cliente.getId()).thenReturn(7L);
        when(context.getClienteAtual()).thenReturn(cliente);
    }

    private Budget budget(String categoria, String centro, LocalDate de, LocalDate ate) {
        var b = new Budget("Orçamento teste", centro, categoria, new BigDecimal("100.00"), de, ate);
        b.setCliente(cliente);
        return b;
    }

    private FinancialTransaction transaction(long id, String valor, LocalDate data) {
        var t = mock(FinancialTransaction.class);
        when(t.getId()).thenReturn(id);
        when(t.getValor()).thenReturn(new BigDecimal(valor));
        when(t.getData()).thenReturn(data);
        when(t.getCategoria()).thenReturn("Categoria principal");
        when(t.getCentroCusto()).thenReturn("Centro principal");
        return t;
    }

    private FinancialAllocation rate(FinancialTransaction t, int bloco, String cat, String centro, String valor) {
        return new FinancialAllocation(t, bloco, cat, new BigDecimal(valor), centro, new BigDecimal(valor));
    }

    private void dados(Budget b, FinancialTransaction t, FinancialAllocation... itens) {
        when(budgets.findAllByClienteIdOrderByDataInicioDesc(7L)).thenReturn(List.of(b));
        when(transactions.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(7L, "DESPESA", b.getDataInicio(), b.getDataFim())).thenReturn(List.of(t));
        when(rates.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L, List.of(t.getId()))).thenReturn(List.of(itens));
    }

    @Test
    void selecionaSomenteRateioDaCategoriaECentroSalvos() {
        var b = budget(" serviços ", " administrativo ", inicio, fim);
        var t = transaction(11L, "100.00", inicio.plusDays(4));
        dados(b, t, rate(t, 1, "Serviços", "Administrativo", "40.00"), rate(t, 2, "Serviços", "Operacional", "50.00"), rate(t, 3, "Outra", "Administrativo", "10.00"));
        var r = service.listar().get(0);
        assertEquals(new BigDecimal("40.00"), r.valorUtilizado());
        assertEquals(new BigDecimal("60.00"), r.valorDisponivel());
        assertEquals(new BigDecimal("40.00"), r.percentualUtilizado());
    }

    @Test
    void orcamentoGeralContaValorIntegralUmaVez() {
        var b = budget(null, null, inicio, fim);
        var t = transaction(11L, "100.00", inicio);
        dados(b, t, rate(t, 1, "A", "Centro A", "30.00"), rate(t, 2, "B", "Centro B", "70.00"));
        assertEquals(new BigDecimal("100.00"), service.listar().get(0).valorUtilizado());
    }

    @Test
    void preservaSinalDoAjusteComRateiosDeSinalInverso() {
        var b = budget("A", null, inicio, fim);
        var t = transaction(11L, "-100.00", inicio);
        dados(b, t, rate(t, 1, "A", "Centro A", "30.00"), rate(t, 2, "B", "Centro B", "70.00"));
        var r = service.listar().get(0);
        assertEquals(new BigDecimal("-30.00"), r.valorUtilizado());
        assertEquals(new BigDecimal("130.00"), r.valorDisponivel());
    }

    @Test
    void mantemPeriodoDeCadaOrcamentoMesmoComNomeIgual() {
        var junho = budget(null, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        var setembro = budget(null, null, inicio, fim);
        var tJunho = transaction(1L, "10.00", junho.getDataInicio());
        var tSetembro = transaction(2L, "30.00", inicio);
        when(budgets.findAllByClienteIdOrderByDataInicioDesc(7L)).thenReturn(List.of(setembro, junho));
        when(transactions.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(7L, "DESPESA", junho.getDataInicio(), junho.getDataFim())).thenReturn(List.of(tJunho));
        when(transactions.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(7L, "DESPESA", inicio, fim)).thenReturn(List.of(tSetembro));
        var r = service.listar();
        assertEquals(new BigDecimal("30.00"), r.get(0).valorUtilizado());
        assertEquals(new BigDecimal("10.00"), r.get(1).valorUtilizado());
    }

    @Test
    void rateioInconsistenteNaoEhAtribuidoACategoriaEspecifica() {
        var b = budget("A", null, inicio, fim);
        var t = transaction(11L, "100.00", inicio);
        dados(b, t, rate(t, 1, "A", "Centro A", "90.00"));
        assertEquals(0, service.listar().get(0).valorUtilizado().signum());
    }

    @Test
    void consultaRateiosSomenteDoClienteAutenticado() {
        var b = budget(null, null, inicio, fim);
        var t = transaction(11L, "100.00", inicio);
        dados(b, t);
        service.listar();
        verify(budgets).findAllByClienteIdOrderByDataInicioDesc(7L);
        verify(rates).findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L, List.of(11L));
        verify(budgets, never()).findAllByOrderByDataInicioDesc();
    }
}
