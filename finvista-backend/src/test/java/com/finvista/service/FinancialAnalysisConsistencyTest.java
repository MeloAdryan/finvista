package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialAnalysisConsistencyTest {

    private final FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
    private final FinancialAllocationRepository allocations = mock(FinancialAllocationRepository.class);
    private final CostCategoryClassificationRepository classifications = mock(CostCategoryClassificationRepository.class);
    private final ClienteContextService context = mock(ClienteContextService.class);
    private final FinancialReferenceService reference = mock(FinancialReferenceService.class);
    private final ExpenseAllocationService allocationService = new ExpenseAllocationService(allocations);
    private final DashboardAnalysisService dashboard = new DashboardAnalysisService(transactions, context, reference, allocationService);
    private final ExpenseExplorerService explorer = new ExpenseExplorerService(transactions, classifications, context, reference, allocationService);

    @BeforeEach
    void configurar() {
        when(context.getClienteAtualId()).thenReturn(7L);
        when(reference.obterMesReferencia()).thenReturn(YearMonth.of(2026, 9));
        var anterior = registro(1, "2026-08-10", "100");
        var atual = registro(2, "2026-09-10", "150");
        when(transactions.findByClienteIdAndDataBetweenOrderByDataAsc(anyLong(), any(), any())).thenReturn(List.of(anterior, atual));
        when(allocations.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(anyLong(), anyList()))
                .thenReturn(List.of(
                        new FinancialAllocation(anterior, 1, "A", new BigDecimal("60"), "Administrativo", new BigDecimal("60")),
                        new FinancialAllocation(anterior, 2, "B", new BigDecimal("40"), "Operacional", new BigDecimal("40")),
                        new FinancialAllocation(atual, 1, "A", new BigDecimal("90"), "Administrativo", new BigDecimal("90")),
                        new FinancialAllocation(atual, 2, "B", new BigDecimal("60"), "Operacional", new BigDecimal("60"))));
    }

    private FinancialTransaction registro(long id, String data, String valor) {
        var t = mock(FinancialTransaction.class);
        when(t.getId()).thenReturn(id);
        when(t.getData()).thenReturn(LocalDate.parse(data));
        when(t.getTipo()).thenReturn("DESPESA");
        when(t.getValor()).thenReturn(new BigDecimal(valor));
        when(t.getCategoria()).thenReturn("A");
        when(t.getCentroCusto()).thenReturn("Administrativo");
        return t;
    }

    @Test
    void mesmosPeriodosEFiltrosReconciliamPainelCategoriasECentros() {
        for (String categoria : new String[]{null, "A", "B"}) {
            var d = dashboard.consultar(null, null, categoria);
            var e = explorer.consultar(null, null, null, categoria, null, null);
            assertEquals(0, d.indicadores().despesa().compareTo(e.total()));
            assertEquals(0, d.indicadores().despesaMesAnterior().compareTo(e.totalAnterior()));
            assertEquals(d.inicioAnterior(), e.inicioAnterior());
            assertEquals(d.fimAnterior(), e.fimAnterior());
            assertEquals(d.registros().despesasAtual(), e.registros().atual());
            assertEquals(0, e.total().compareTo(e.categorias().stream().map(item -> item.atual()).reduce(BigDecimal.ZERO, BigDecimal::add)));
            assertEquals(0, e.total().compareTo(e.centros().stream().map(item -> item.atual()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        verify(transactions, atLeastOnce()).findByClienteIdAndDataBetweenOrderByDataAsc(eq(7L), any(), any());
    }

    @Test
    void intervaloPersonalizadoPreservaMesmaComparacao() {
        var inicio = LocalDate.of(2026, 9, 5);
        var fim = LocalDate.of(2026, 9, 20);
        var d = dashboard.consultar(inicio, fim, "A");
        var e = explorer.consultar(inicio, fim, null, "A", null, null);
        assertEquals(d.inicioAnterior(), e.inicioAnterior());
        assertEquals(d.fimAnterior(), e.fimAnterior());
        assertEquals(0, d.indicadores().despesa().compareTo(e.total()));
    }
}


             