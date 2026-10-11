package com.finvista.integration.contaazul;

import com.finvista.controller.ContaAzulParcelDetailController;
import com.finvista.service.ClienteContextService;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulParcelDetailControllerTest {

    static final String ID = ContaAzulParcelDetailClientTest.ID;
    ContaAzulConnectionService connections;
    ContaAzulParcelDetailClient client;
    ClienteContextService ctx;
    ContaAzulParcelDetailController controller;

    @BeforeEach
    void setup() {
        connections = mock(ContaAzulConnectionService.class);
        client = mock(ContaAzulParcelDetailClient.class);
        ctx = mock(ClienteContextService.class);
        controller = new ContaAzulParcelDetailController(connections, client, ctx);
    }

    @Test
    void invalidPathRejectedBeforeTokenAccess() {
        assertThrows(IllegalArgumentException.class, () -> controller.consultar("../other", 1L));
        verifyNoInteractions(connections, client);
    }

    @Test
    void abbreviatedUuidRejected() {
        assertThrows(IllegalArgumentException.class, () -> controller.consultar("1-1-1-1-1", 1L));
        verifyNoInteractions(connections, client);
    }

    @Test
    void invalidCompanyRejected() {
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(ID, 0L));
        verifyNoInteractions(connections, client);
    }

    @Test
    void nonAdminRejected() {
        doThrow(new IllegalStateException("Negado")).when(ctx).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> controller.consultar(ID, 1L));
        verifyNoInteractions(connections, client);
    }

    @Test
    void otherCompanyRejectedByConnectionBeforeProvider() {
        when(connections.acessoParaConsulta(2L)).thenThrow(new IllegalArgumentException("Empresa mudou"));
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(ID, 2L));
        verifyNoInteractions(client);
    }

    void ready() {
        when(connections.acessoParaConsulta(1L)).thenReturn("secret");
        when(client.consultar("secret", ID)).thenReturn(new ContaAzulParcelDetail(ID, "Teste", null, null, null, null, null, null, null));
    }

    @Test
    void companyChangedDuringRequestDiscardsResult() {
        ready();
        when(ctx.getClienteAtualId()).thenReturn(2L);
        assertThrows(IllegalArgumentException.class, () -> controller.consultar(ID, 1L));
    }

    @Test
    void validResponseDoesNotContainCredential() {
        ready();
        when(ctx.getClienteAtualId()).thenReturn(1L);
        var response = controller.consultar(ID, 1L);
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(ID, response.getBody().parcela().id());
        assertFalse(response.getBody().toString().contains("secret"));
    }
}
