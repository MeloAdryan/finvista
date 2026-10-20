package com.finvista.integration.contaazul;

import com.finvista.controller.ContaAzulOAuthController;
import com.finvista.service.ClienteContextService;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulLocalCallbackTest {
    private final ContaAzulProperties p = new ContaAzulProperties();
    private final ContaAzulConnectionService connections = mock(ContaAzulConnectionService.class);
    private final ClienteContextService context = mock(ClienteContextService.class);
    private final MockEnvironment environment = new MockEnvironment();
    private ContaAzulOAuthController controller;
    private final String state = "a".repeat(43);

    @BeforeEach void setup() {
        p.setRedirectUri("https://test.trycloudflare.com/api/integracoes/conta-azul/callback");
        controller = new ContaAzulOAuthController(p, mock(ContaAzulConfiguration.class), mock(ContaAzulOAuthService.class), connections, context);
        environment.setActiveProfiles("dev");
        ReflectionTestUtils.setField(controller, "environment", environment);
        ReflectionTestUtils.setField(controller, "localCallbackRelay", true);
        doThrow(new IllegalStateException("Sem sessao local")).when(context).validarAdministrador();
    }

    private MockHttpServletRequest request(String host) {
        var request = new MockHttpServletRequest(); request.setServerName(host); return request;
    }

    @Test void tunelEncaminhaSemTrocarCodigoNemConsumirSessao() {
        var response = controller.callback(request("test.trycloudflare.com"), "code+&", state, null);
        assertEquals(303, response.getStatusCode().value());
        assertEquals("http://localhost:8080/api/integracoes/conta-azul/callback?state=" + state + "&code=code%2B%26", response.getHeaders().getLocation().toString());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("no-referrer", response.getHeaders().getFirst("Referrer-Policy"));
        verifyNoInteractions(context, connections);
    }
    @Test void retornoLocalAindaExigeAdministrador() {
        assertThrows(IllegalStateException.class, () -> controller.callback(request("localhost"), "code", state, null));
        verifyNoInteractions(connections);
    }
    @Test void dominioDiferenteNaoUsaEncaminhamento() {
        assertThrows(IllegalStateException.class, () -> controller.callback(request("other.test"), "code", state, null));
        verifyNoInteractions(connections);
    }
    @Test void producaoNuncaEncaminhaMesmoComFlagLigada() {
        environment.setActiveProfiles("dev", "prod");
        assertThrows(IllegalStateException.class, () -> controller.callback(request("test.trycloudflare.com"), "code", state, null));
        verifyNoInteractions(connections);
    }
    @Test void stateAusenteNaoEncaminha() {
        assertThrows(IllegalArgumentException.class, () -> controller.callback(request("test.trycloudflare.com"), "code", null, null));
        verifyNoInteractions(context, connections);
    }
}
