package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Probes isolated from the project and production database.
 */
class AuditCrossPageTest {

    final FinancialTransactionRepository tx = mock(FinancialTransactionRepository.class);
    final FinancialAllocationRepository rates = mock(FinancialAllocationRepository.class);
    final ClienteContextService ctx = mock(ClienteContextService.class);
    final FinancialReferenceService ref = mock(FinancialReferenceService.class);
    final BudgetRepository budgets = mock(BudgetRepository.class);
    final CostCategoryClassificationRepository classes = mock(CostCategoryClassificationRepository.class);
    final ExpenseAllocationService split = new ExpenseAllocationService(rates);
    final LocalDate start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31);
    final Cliente client = mock(Cliente.class);
    final FinancialTransaction expense = item("DESPESA", "100.00", "Serviços");
    final FinancialTransaction income = item("RECEITA", "200.00", "Consultas");

    AuditCrossPageTest() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(ctx.getClienteAtual()).thenReturn(client);
        when(client.getId()).thenReturn(7L);
        when(ref.obterMesReferencia()).thenReturn(YearMonth.of(2026, 10));
        org.springframework.test.util.ReflectionTestUtils.setField(expense, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(income, "id", 2L);
        income.setValorRealizado(new BigDecimal("50.00"));
        income.setValorAberto(new BigDecimal("150.00"));
        expense.setValorAberto(BigDecimal.ZERO);
        expense.setSituacao("QUITADO");
        when(tx.findByClienteIdAndDataBetweenOrderByDataAsc(eq(7L), any(), any())).thenReturn(List.of(expense, income));
        when(tx.findByClienteIdAndTipoAndDataBetweenOrderByDataAsc(eq(7L), eq("DESPESA"), any(), any())).thenReturn(List.of(expense));
        when(tx.findByClienteId(7L)).thenReturn(List.of(expense, income));
        when(rates.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(eq(7L), anyList())).thenReturn(List.of(
                new FinancialAllocation(expense, 1, "Serviços", new BigDecimal("40.00"), "Administrativo", new BigDecimal("40.00")),
                new FinancialAllocation(expense, 2, "Materiais", new BigDecimal("60.00"), "Operacional", new BigDecimal("60.00"))));
    }

    FinancialTransaction item(String type, String value, String cat) {
        var t = new FinancialTransaction();
        t.setData(start.plusDays(5));
        t.setDataCompetencia(t.getData());
        t.setDataVencimento(end);
        t.setTipo(type);
        t.setValor(new BigDecimal(value));
        t.setDescricao("Teste isolado");
        t.setCategoria(cat);
        return t;
    }

    BigDecimal budget(String cat) {
        var b = new Budget("Teste", null, cat, new BigDecimal("500.00"), start, end);
        b.setCliente(client);
        when(budgets.findAllByClienteIdOrderByDataInicioDesc(7L)).thenReturn(List.of(b));
        return new BudgetPipelineService(budgets, tx, ctx, split).listar().get(0).valorUtilizado();
    }

    @Test
    void totalsAgreeAcrossServicesWithoutDuplicatingAllocations() {
        var d = new DashboardAnalysisService(tx, ctx, ref, split).consultar(start, end, null);
        var e = new ExpenseExplorerService(tx, classes, ctx, ref, split).consultar(start, end, null, null, null, null);
        assertEquals(new BigDecimal("100.00"), d.indicadores().despesa());
        assertEquals(d.indicadores().despesa(), e.total());
        assertEquals(e.total(), budget(null));
        assertEquals(e.total(), new FinancialTransactionAggregationService(tx, ctx).obterResumoMensal(start, end).get(0).despesa());
        assertEquals(e.total(), new FinancialDetailService(tx, ctx, ref).consultar("DESPESA", YearMonth.of(2026, 10), 0, 10).total());
        assertEquals(new BigDecimal("50.00"), d.meses().get(0).recebimentos().recebido());
        assertEquals(new BigDecimal("150.00"), d.meses().get(0).recebimentos().emAberto());
    }

    @Test
    void categoryFilterUsesOnlyItsShareAcrossPages() {
        var d = new DashboardAnalysisService(tx, ctx, ref, split).consultar(start, end, "Serviços");
        var e = new ExpenseExplorerService(tx, classes, ctx, ref, split).consultar(start, end, null, "Serviços", null, null);
        assertEquals(new BigDecimal("40.00"), d.indicadores().despesa());
        assertEquals(d.indicadores().despesa(), e.total());
        assertEquals(e.total(), budget("Serviços"));
    }

    @Test
    void cancelamentoEhExcluidoDeTodosOsTotaisAtivos() {
        expense.setSituacao("CANCELADO");
        expense.setValorAberto(new BigDecimal("100.00"));
        var d = new DashboardAnalysisService(tx, ctx, ref, split).consultar(start, end, null);
        assertEquals(0, d.indicadores().despesa().signum());
        assertEquals(0, budget(null).signum());
        assertEquals(0, new FinancialTransactionAggregationService(tx, ctx).obterResumoMensal(start, end).get(0).despesa().signum());
        assertEquals(0, new FinancialPositionService(tx, ctx).consultar(start).pagar().aVencer().signum());
    }
}
