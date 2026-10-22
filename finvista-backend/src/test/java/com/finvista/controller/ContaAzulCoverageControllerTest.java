package com.finvista.controller;

import com.finvista.integration.contaazul.*;
import com.finvista.repository.*;
import com.finvista.service.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulCoverageControllerTest {

    final ContaAzulFinancialClient api = mock(ContaAzulFinancialClient.class);
    final ContaAzulConnectionService connection = mock(ContaAzulConnectionService.class);
    final FinancialTransactionRepository tx = mock(FinancialTransactionRepository.class);
    final ClienteContextService ctx = mock(ClienteContextService.class);
    final ContaAzulCoverageController controller = new ContaAzulCoverageController(api, connection, tx, ctx);
    final LocalDate inicio = LocalDate.parse("2026-10-01"), fim = LocalDate.parse("2026-10-31"), vencInicio = LocalDate.parse("2027-01-01"), vencFim = LocalDate.parse("2027-12-31");

    @Test
    void consultaCompetenciaComVencimentoExternoSemGravar() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(connection.acessoParaConsulta(7L)).thenReturn("test");
        var p = new ContaAzulFinancialClient.Parcela("parcela", "Teste", LocalDate.parse("2027-01-05"), LocalDate.parse("2026-10-15"), "PENDENTE", null, null, null, null, List.of(), List.of());
        when(api.consultarPorCompetencia("test", ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, vencInicio, vencFim, 1)).thenReturn(new ContaAzulFinancialClient.Pagina(1, List.of(p)));
        when(tx.findByClienteId(7L)).thenReturn(List.of());
        var r = controller.consultar(7L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, vencInicio, vencFim, 1).getBody();
        assertNotNull(r);
        assertEquals("COMPETENCIA", r.criterio());
        assertFalse(r.itens().get(0).vinculoApiLocal());
        assertEquals(vencInicio, r.vencimentoInicio());
        assertEquals(LocalDate.parse("2027-01-05"), r.itens().get(0).parcela().vencimento());
        var node = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(r);
        assertTrue(node.at("/itens/0/parcela").has("vencimento"));
        assertTrue(node.at("/itens/0/parcela").has("competencia"));
        verify(tx, never()).save(any());
        verify(tx, never()).saveAndFlush(any());
    }

    @Test
    void recusaEmpresaDiferenteAntesDeChamarFonte() {
        when(ctx.getClienteAtualId()).thenReturn(8L);
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(7L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, vencInicio, vencFim, 1));
        verifyNoInteractions(api, connection, tx);
    }

    @Test
    void recusaJanelaMaiorQue366Dias() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(7L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, vencInicio, vencFim.plusDays(2), 1));
        verifyNoInteractions(api, connection, tx);
    }
}
