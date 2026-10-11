package com.finvista.integration.contaazul;

import com.finvista.controller.ContaAzulFinancialController;
import com.finvista.service.ClienteContextService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulFinancialControllerTest {

    ContaAzulConnectionService connections;
    ContaAzulFinancialClient client;
    ClienteContextService ctx;
    ContaAzulFinancialController controller;
    final LocalDate inicio = LocalDate.of(2026, 10, 1), fim = LocalDate.of(2026, 10, 31);

    @BeforeEach
    void setup() {
        connections = mock(ContaAzulConnectionService.class);
        client = mock(ContaAzulFinancialClient.class);
        ctx = mock(ClienteContextService.class);
        controller = new ContaAzulFinancialController(connections, client, ctx);
    }

    @Test
    void noAdminNoProviderCall() {
        doThrow(new IllegalStateException("Acesso negado")).when(ctx).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> controller.consultar(1L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, 1));
        verifyNoInteractions(connections, client);
    }

    @Test
    void invalidDatesNoProviderCall() {
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(1L, ContaAzulFinancialClient.Tipo.DESPESA, fim, inicio, 1));
        verifyNoInteractions(connections, client);
    }

    @Test
    void tooLongIntervalNoProviderCall() {
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(1L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, inicio.plusDays(366), 1));
        verifyNoInteractions(connections, client);
    }

    @Test
    void invalidPageNoProviderCall() {
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(1L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, 0));
        verifyNoInteractions(connections, client);
    }

    @Test
    void missingAuthorizationNoProviderCall() {
        when(connections.acessoParaConsulta(1L)).thenThrow(new IllegalArgumentException("Não autorizada"));
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(1L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, 1));
        verifyNoInteractions(client);
    }

    void ready() {
        when(connections.acessoParaConsulta(1L)).thenReturn("secret");
        when(client.consultar("secret", ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, 1)).thenReturn(new ContaAzulFinancialClient.Pagina(0, List.of()));
    }

    @Test
    void changedCompanyAfterRequestDiscarded() {
        ready();
        when(ctx.getClienteAtualId()).thenReturn(2L);
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(1L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, 1));
    }

    @Test
    void responseHasNoTokenAndNoCache() {
        ready();
        when(ctx.getClienteAtualId()).thenReturn(1L);
        var response = controller.consultar(1L, ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, 1);
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(1L, response.getBody().clienteId());
        assertFalse(response.getBody().toString().contains("secret"));
    }

    @Test
    void providerMessageHasJsonAndCorrectStatus() {
        var response = controller.falhaConsulta(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Aguarde antes de tentar novamente."));
        assertEquals(429, response.getStatusCode().value());
        assertEquals("Aguarde antes de tentar novamente.", response.getBody().get("mensagem"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }
}
