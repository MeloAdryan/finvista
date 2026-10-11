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

class AnalysisRecordCoverageTest {

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
        when(reference.obterMesReferencia()).thenReturn(YearMonth.of(2026, 10));
        when(allocations.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(anyLong(), anyList())).thenReturn(List.of());
    }

    private FinancialTransaction registro(long id, String data, String tipo, String valor, String categoria) {
        var t = mock(FinancialTransaction.class);
        when(t.getId()).thenReturn(id);
        when(t.getData()).thenReturn(LocalDate.parse(data));
        when(t.getTipo()).thenReturn(tipo);
        when(t.getValor()).thenReturn(new BigDecimal(valor));
        when(t.getCategoria()).thenReturn(categoria);
        when(t.getCentroCusto()).thenReturn("Administrativo");
        return t;
    }

    private void dados(FinancialTransaction... registros) {
        when(transactions.findByClienteIdAndDataBetweenOrderByDataAsc(anyLong(), any(), any())).thenReturn(List.of(registros));
    }

    @Test
    void ausenciaRetornaContagensZeroNosDoisServicos() {
        dados();
        var d = dashboard.consultar(null, null, null);
        var e = explorer.consultar(null, null, null, null, null, null);
        assertEquals(0, d.registros().receitasAtual());
        assertEquals(0, d.registros().despesasAtual());
        assertEquals(0, d.registros().receitasAnterior());
        assertEquals(0, d.registros().despesasAnterior());
        assertEquals(0, e.registros().atual());
        assertEquals(0, e.registros().anterior());
    }

    @Test
    void somaZeroPreservaExistenciaDeRegistrosEEstornos() {
        dados(registro(1, "2026-10-10", "DESPESA", "100", "A"),
                registro(2, "2026-10-11", "DESPESA", "-100", "A"),
                registro(3, "2026-09-10", "DESPESA", "0", "A"));
        var d = dashboard.consultar(null, null, null);
        var e = explorer.consultar(null, null, null, null, null, null);
        assertEquals(0, d.indicadores().despesa().signum());
        assertEquals(0, e.total().signum());
        assertEquals(2, d.registros().despesasAtual());
        assertEquals(1, d.registros().despesasAnterior());
        assertEquals(2, e.registros().atual());
        assertEquals(1, e.registros().anterior());
    }

    @Test
    void receitaAnteriorNaoCriaBaseDeDespesas() {
        dados(registro(1, "2026-09-10", "RECEITA", "100", "A"),
                registro(2, "2026-10-10", "DESPESA", "80", "A"));
        var d = dashboard.consultar(null, null, null);
        var e = explorer.consultar(null, null, null, null, null, null);
        assertEquals(1, d.registros().receitasAnterior());
        assertEquals(0, d.registros().despesasAnterior());
        assertEquals(1, d.registros().despesasAtual());
        assertEquals(0, e.registros().anterior());
        assertEquals(1, e.registros().atual());
    }

    @Test
    void categoriaSemCorrespondenciaNaoContaRegistrosDeOutrasCategorias() {
        dados(registro(1, "2026-10-10", "DESPESA", "80", "A"));
        assertEquals(0, dashboard.consultar(null, null, "B").registros().despesasAtual());
        assertEquals(0, explorer.consultar(null, null, null, "B", null, null).registros().atual());
    }

    @Test
    void rateiosNaoDuplicamContagemDoLancamento() {
        var t = registro(1, "2026-10-10", "DESPESA", "100", "A");
        dados(t);
        when(allocations.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(anyLong(), anyList())).thenReturn(List.of(
                new FinancialAllocation(t, 1, "A", new BigDecimal("35"), "Administrativo", new BigDecimal("35")),
                new FinancialAllocation(t, 2, "A", new BigDecimal("65"), "Administrativo", new BigDecimal("65"))));
        var d = dashboard.consultar(null, null, "A");
        var e = explorer.consultar(null, null, "Administrativo", "A", null, null);
        assertEquals(1, d.registros().despesasAtual());
        assertEquals(1, e.registros().atual());
        assertEquals(new BigDecimal("100"), e.total());
    }

    @Test
    void centroEComportamentoAplicamMesmoEscopoDaContagem() {
        dados(registro(1, "2026-10-10", "DESPESA", "80", "A"));
        assertEquals(0, explorer.consultar(null, null, "Operacional", null, null, null).registros().atual());
        assertEquals(0, explorer.consultar(null, null, null, null, CostBehavior.FIXO, null).registros().atual());
        assertEquals(1, explorer.consultar(null, null, null, null, CostBehavior.NAO_CLASSIFICADO, null).registros().atual());
    }
}
