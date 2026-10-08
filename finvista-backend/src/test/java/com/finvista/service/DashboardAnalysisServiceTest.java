package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardAnalysisServiceTest {

    FinancialTransactionRepository transactions;
    FinancialAllocationRepository allocations;
    ClienteContextService contexto;
    FinancialReferenceService referencia;
    DashboardAnalysisService service;

    @BeforeEach
    void preparar() {
        transactions = mock(FinancialTransactionRepository.class);
        allocations = mock(FinancialAllocationRepository.class);
        contexto = mock(ClienteContextService.class);
        referencia = mock(FinancialReferenceService.class);
        when(contexto.getClienteAtualId()).thenReturn(1L);
        when(referencia.obterMesReferencia()).thenReturn(YearMonth.of(2026, 10));
        when(allocations.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(anyLong(), anyList())).thenReturn(List.of());
        service = new DashboardAnalysisService(transactions, contexto, referencia, new ExpenseAllocationService(allocations));
    }

    FinancialTransaction tx(long id, String data, String tipo, String valor, String categoria) {
        FinancialTransaction t = mock(FinancialTransaction.class);
        when(t.getId()).thenReturn(id);
        when(t.getData()).thenReturn(LocalDate.parse(data));
        when(t.getTipo()).thenReturn(tipo);
        when(t.getValor()).thenReturn(new BigDecimal(valor));
        when(t.getCategoria()).thenReturn(categoria);
        when(t.getDescricao()).thenReturn("Lançamento " + id);
        return t;
    }

    void dados(FinancialTransaction... ts) {
        when(transactions.findByClienteIdAndDataBetweenOrderByDataAsc(anyLong(), any(), any())).thenReturn(List.of(ts));
    }

    @Test
    void periodoInteiroFechaCartoesMesesDetalhesEVariacoes() {
        dados(tx(1, "2026-09-10", "DESPESA", "80", "A"), tx(2, "2026-10-10", "DESPESA", "100", "A"), tx(3, "2026-10-12", "RECEITA", "200", "B"));
        var r = service.consultar(null, null, null);
        assertEquals(new BigDecimal("100"), r.indicadores().despesa());
        assertEquals(new BigDecimal("100"), r.indicadores().resultado());
        assertEquals(0, r.meses().stream().map(m -> m.despesa()).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(r.indicadores().despesa()));
        assertEquals(0, r.itens().stream().filter(i -> i.tipo().equals("DESPESA")).map(i -> i.valorSelecionado()).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(r.indicadores().despesa()));
        assertEquals(new BigDecimal("20"), r.variacoes().get(0).diferenca());
        verify(transactions).findByClienteIdAndDataBetweenOrderByDataAsc(1L, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"));
    }

    @Test
    void filtroRespeitaRateioSecundarioSemDuplicarParcela() {
        FinancialTransaction t = tx(7, "2026-10-10", "DESPESA", "100", "Principal");
        dados(t);
        FinancialAllocation a = mock(FinancialAllocation.class), b = mock(FinancialAllocation.class);
        when(a.getLancamento()).thenReturn(t);
        when(b.getLancamento()).thenReturn(t);
        when(a.getBloco()).thenReturn(1);
        when(b.getBloco()).thenReturn(2);
        when(a.getCategoria()).thenReturn("Principal");
        when(b.getCategoria()).thenReturn("Juros");
        when(a.getValorCategoria()).thenReturn(new BigDecimal("-80"));
        when(b.getValorCategoria()).thenReturn(new BigDecimal("-20"));
        when(allocations.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(eq(1L), anyList())).thenReturn(List.of(a, b));
        var r = service.consultar(null, null, " juros ");
        assertEquals(new BigDecimal("20"), r.indicadores().despesa());
        assertEquals(1, r.itens().size());
        assertEquals(new BigDecimal("100"), r.itens().get(0).valorContabilizadoIntegral());
        assertEquals(List.of("Juros"), r.itens().get(0).categorias());
        verify(allocations, times(1)).findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(eq(1L), anyList());
    }

    @Test
    void intervaloPersonalizadoComparaMesmaQuantidadeDeDias() {
        dados(tx(1, "2026-09-30", "DESPESA", "50", "A"), tx(2, "2026-10-07", "DESPESA", "60", "A"));
        var r = service.consultar(LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-14"), null);
        assertEquals(LocalDate.parse("2026-09-29"), r.inicioAnterior());
        assertEquals(LocalDate.parse("2026-10-06"), r.fimAnterior());
        assertEquals(new BigDecimal("50"), r.indicadores().despesaMesAnterior());
        assertEquals(new BigDecimal("60"), r.indicadores().despesa());
    }

    @Test
    void baseAnteriorZeroNaoInventaPercentual() {
        dados(tx(1, "2026-10-03", "RECEITA", "50", "A"));
        var r = service.consultar(null, null, null);
        assertNull(r.indicadores().variacaoReceita());
    }

    @Test
    void mesesCompletosComparamMesesCompletosETenantAtual() {
        when(contexto.getClienteAtualId()).thenReturn(2L);
        dados();
        var r = service.consultar(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"), null);
        assertEquals(LocalDate.parse("2026-07-01"), r.inicioAnterior());
        assertEquals(LocalDate.parse("2026-08-31"), r.fimAnterior());
        assertEquals(2L, r.clienteId());
        assertEquals(2, r.meses().size());
        verify(transactions).findByClienteIdAndDataBetweenOrderByDataAsc(2L, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-10-31"));
    }

    @Test
    void validacaoNaoConsultaIntervaloIncompletoOuInvertido() {
        assertThrows(IllegalArgumentException.class, () -> service.consultar(LocalDate.parse("2026-10-01"), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.consultar(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01"), null));
        assertThrows(IllegalArgumentException.class, () -> service.consultar(LocalDate.parse("2024-01-01"), LocalDate.parse("2026-10-31"), null));
        verifyNoInteractions(transactions);
    }
}
