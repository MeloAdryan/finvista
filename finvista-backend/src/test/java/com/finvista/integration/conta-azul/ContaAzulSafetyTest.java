package com.finvista.integration.contaazul;

import com.finvista.controller.ContaAzulOAuthController;
import com.finvista.model.Usuario;
import com.finvista.service.ClienteContextService;
import java.util.Base64;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulSafetyTest {

    ContaAzulProperties p;
    ContaAzulTokenCipher cipher;

    @BeforeEach
    void setup() {
        p = new ContaAzulProperties();
        p.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new ContaAzulTokenCipher(p);
    }

    @Test
    void roundTrip() {
        String s = cipher.cifrar("secret", 1L, "access");
        assertEquals("secret", cipher.decifrar(s, 1L, "access"));
        assertFalse(s.contains("secret"));
    }

    @Test
    void randomIv() {
        assertNotEquals(cipher.cifrar("same", 1L, "access"), cipher.cifrar("same", 1L, "access"));
    }

    @Test
    void otherCompanyRejected() {
        String s = cipher.cifrar("secret", 1L, "access");
        assertThrows(IllegalStateException.class, () -> cipher.decifrar(s, 2L, "access"));
    }

    @Test
    void otherTokenKindRejected() {
        String s = cipher.cifrar("secret", 1L, "access");
        assertThrows(IllegalStateException.class, () -> cipher.decifrar(s, 1L, "refresh"));
    }

    @Test
    void wrongKeyRejected() {
        String s = cipher.cifrar("secret", 1L, "access");
        byte[] key = new byte[32];
        key[0] = 1;
        p.setEncryptionKey(Base64.getEncoder().encodeToString(key));
        assertThrows(IllegalStateException.class, () -> cipher.decifrar(s, 1L, "access"));
    }

    @Test
    void damagedEnvelopeRejected() {
        assertThrows(IllegalStateException.class, () -> cipher.decifrar("v1.broken.broken", 1L, "access"));
    }

    @Test
    void disabledByDefault() {
        assertFalse(p.isEnabled());
        assertThrows(IllegalStateException.class, () -> new ContaAzulConfiguration(p).exigirAtiva());
    }

    @Test
    void incompleteConfigurationRejected() {
        p.setEnabled(true);
        assertFalse(new ContaAzulConfiguration(p).pronta());
    }

    @Test
    void validConfiguration() {
        configure();
        assertTrue(new ContaAzulConfiguration(p).pronta());
    }

    @Test
    void httpCallbackRejected() {
        configure();
        p.setRedirectUri("http://example.com/api/integracoes/conta-azul/callback");
        assertFalse(new ContaAzulConfiguration(p).pronta());
    }

    void configure() {
        p.setClientId("id");
        p.setClientSecret("secret");
        p.setRedirectUri("https://example.com/api/integracoes/conta-azul/callback");
    }
    ContaAzulConnectionService connections;
    ClienteContextService ctx;
    ContaAzulOAuthController controller;
    MockHttpServletRequest req;

    void flow() {
        configure();
        p.setEnabled(true);
        connections = mock(ContaAzulConnectionService.class);
        ctx = mock(ClienteContextService.class);
        Usuario user = mock(Usuario.class);
        when(user.getId()).thenReturn(10L);
        when(ctx.getUsuarioAutenticado()).thenReturn(user);
        when(ctx.getClienteAtualId()).thenReturn(1L);
        controller = new ContaAzulOAuthController(p, new ContaAzulConfiguration(p), new ContaAzulOAuthService(p), connections, ctx);
        req = new MockHttpServletRequest();
        controller.iniciar(req);
    }

    ContaAzulOAuthController.Pendente pending() {
        return (ContaAzulOAuthController.Pendente) req.getSession().getAttribute("FINVISTA_CONTA_AZUL_OAUTH");
    }

    @Test
    void successfulCallbackAndReplayRejected() {
        flow();
        String state = pending().state();
        controller.callback(req, "code", state, null);
        verify(connections).conectar(1L, "code");
        assertThrows(IllegalArgumentException.class, () -> controller.callback(req, "code", state, null));
        verifyNoMoreInteractions(connections);
    }

    @Test
    void wrongStateRejected() {
        flow();
        assertThrows(IllegalArgumentException.class, () -> controller.callback(req, "code", "other", null));
        verifyNoInteractions(connections);
    }

    @Test
    void changedCompanyRejected() {
        flow();
        String state = pending().state();
        when(ctx.getClienteAtualId()).thenReturn(2L);
        assertThrows(IllegalArgumentException.class, () -> controller.callback(req, "code", state, null));
        verifyNoInteractions(connections);
    }

    @Test
    void expiredStateRejected() {
        flow();
        var v = pending();
        req.getSession().setAttribute("FINVISTA_CONTA_AZUL_OAUTH", new ContaAzulOAuthController.Pendente(v.state(), v.clienteId(), v.usuarioId(), Instant.now().minusSeconds(1).getEpochSecond()));
        assertThrows(IllegalArgumentException.class, () -> controller.callback(req, "code", v.state(), null));
        verifyNoInteractions(connections);
    }

    @Test
    void providerDenialRejected() {
        flow();
        String state = pending().state();
        assertThrows(IllegalArgumentException.class, () -> controller.callback(req, null, state, "denied"));
        verifyNoInteractions(connections);
    }

    @Test
    void missingSessionRejected() {
        flow();
        req = new MockHttpServletRequest();
        assertThrows(IllegalArgumentException.class, () -> controller.callback(req, "code", "state", null));
        verifyNoInteractions(connections);
    }
}
