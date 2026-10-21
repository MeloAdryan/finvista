package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardReceiptAnalysisTest {

    @Test
    void composicaoMensalPreservaCompetenciaFiltroClienteEIndicadores() {
        var repo = mock(FinancialTransactionRepository.class);
        var allocation = mock(FinancialAllocationRepository.class);
        var context = mock(ClienteContextService.class);
        var reference = mock(FinancialReferenceService.class);
        when(context.getClienteAtualId()).thenReturn(7L);
        when(reference.obterMesReferencia()).thenReturn(YearMonth.of(2026, 10));
        var t = new FinancialTransaction(LocalDate.of(2026, 10, 4), "Receita", "RECEITA", new BigDecimal("100"), "Serviços", null, "CONTA_AZUL_API", "abc");
        t.setDataRealizacao(LocalDate.of(2026, 11, 2));
        t.setValorRealizado(new BigDecimal("40"));
        t.setValorAberto(new BigDecimal("60"));
        when(repo.findByClienteIdAndDataBetweenOrderByDataAsc(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31))).thenReturn(List.of(t));
        var service = new DashboardAnalysisService(repo, context, reference, new ExpenseAllocationService(allocation));
        var result = service.consultar(null, null, "Serviços");
        var m = result.meses().get(0);
        assertEquals("2026-10", m.periodo());
        assertEquals(new BigDecimal("100"), result.indicadores().receita());
        assertEquals(new BigDecimal("40"), m.recebimentos().recebido());
        assertEquals(new BigDecimal("60"), m.recebimentos().emAberto());
        assertEquals(0, m.recebimentos().registrosSemDetalhamento());
        assertEquals(7L, result.clienteId());
        verify(repo).findByClienteIdAndDataBetweenOrderByDataAsc(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));
    }
}
