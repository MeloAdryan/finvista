package com.finvista.controller;

import com.finvista.service.ClienteContextService;
import com.finvista.service.ClienteService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClienteContextResponseTest {
    @Test
    void administradorSemEmpresaRecebeRespostaVazia() {
        var contexto = mock(ClienteContextService.class);
        when(contexto.isAdmin()).thenReturn(true);
        when(contexto.getClienteSelecionadoId()).thenReturn(null);
        var controller = new ClienteController(mock(ClienteService.class), contexto);
        var resposta = controller.contextoAtual();
        assertEquals(204, resposta.getStatusCode().value());
        assertNull(resposta.getBody());
        verify(contexto, never()).getClienteAtual();
    }

    @Test
    void empresaSelecionadaInvalidaNaoETratadaComoAusencia() {
        var contexto = mock(ClienteContextService.class);
        when(contexto.isAdmin()).thenReturn(true);
        when(contexto.getClienteSelecionadoId()).thenReturn(7L);
        when(contexto.getClienteAtual()).thenThrow(new IllegalStateException("Empresa inativa"));
        var controller = new ClienteController(mock(ClienteService.class), contexto);
        assertThrows(IllegalStateException.class, controller::contextoAtual);
    }

    @Test
    void usuarioComumContinuaPassandoPelaValidacaoDeAcesso() {
        var contexto = mock(ClienteContextService.class);
        when(contexto.getClienteAtual()).thenThrow(new IllegalStateException("Sem acesso"));
        var controller = new ClienteController(mock(ClienteService.class), contexto);
        assertThrows(IllegalStateException.class, controller::contextoAtual);
        verify(contexto, never()).getClienteSelecionadoId();
    }
}
