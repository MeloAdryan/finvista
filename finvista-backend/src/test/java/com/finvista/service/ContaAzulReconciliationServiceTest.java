package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulReconciliationServiceTest {

    final ClienteContextService ctx = mock(ClienteContextService.class);
    final FinancialViewService view = mock(FinancialViewService.class);
    final LocalDate inicio = LocalDate.of(2026, 9, 1), fim = LocalDate.of(2026, 9, 30);

    FinancialViewService.Resposta response(String receipt, String expense, boolean empty) {
        var r = mock(FinancialViewService.Resposta.class);
        when(r.clienteId()).thenReturn(7L);
        when(r.totais()).thenReturn(new FinancialViewService.Total(new BigDecimal(receipt), new BigDecimal(expense), new BigDecimal(receipt).subtract(new BigDecimal(expense))));
        when(r.linhas()).thenReturn(empty ? List.of() : List.of(new FinancialViewService.Linha(1L, "x", "Item", "DESPESA", inicio, inicio, inicio, "C", "Centro", BigDecimal.ONE)));
        when(r.exclusoes()).thenReturn(Map.of());
        return r;
    }

    void setup(String apiR, String apiD, String fileR, String fileD, boolean empty) {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(view.consultar(any())).thenAnswer(i -> ((FinancialViewService.Filtro) i.getArgument(0)).criterio() == FinancialViewService.Criterio.BAIXA ? response(apiR, apiD, false) : response(fileR, fileD, empty));
    }

    @Test
    void detectaDivergenciaRecebidaMesmoComDespesasIguais() {
        setup("120.00", "113958.02", "100.00", "113958.02", false);
        var r = new ContaAzulReconciliationService(ctx, view).conferir(7L, inicio, fim);
        assertEquals("DIVERGENCIA", r.situacao());
        assertEquals(new BigDecimal("20.00"), r.diferencaReceita());
        assertEquals(0, r.diferencaDespesa().signum());
        verify(view, times(2)).consultar(any());
    }

    @Test
    void totaisIguaisNaoCertificamVinculoNemCobertura() {
        setup("100", "200", "100", "200", false);
        assertEquals("TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS", new ContaAzulReconciliationService(ctx, view).conferir(7L, inicio, fim).situacao());
    }

    @Test
    void extratoAusenteNaoEquivaleAConferenciaZerada() {
        setup("100", "200", "0", "0", true);
        assertEquals("SEM_DADOS_COMPARAVEIS", new ContaAzulReconciliationService(ctx, view).conferir(7L, inicio, fim).situacao());
    }

    @Test
    void nuncaConsultaOutraEmpresa() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        assertThrows(IllegalArgumentException.class, () -> new ContaAzulReconciliationService(ctx, view).conferir(8L, inicio, fim));
        verifyNoInteractions(view);
    }
}

