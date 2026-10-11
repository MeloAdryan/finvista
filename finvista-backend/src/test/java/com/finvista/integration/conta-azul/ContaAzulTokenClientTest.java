package com.finvista.integration.contaazul;

import org.junit.jupiter.api.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class ContaAzulTokenClientTest {

    MockRestServiceServer server;
    ContaAzulTokenClient client;

    @BeforeEach
    void setup() {
        var p = new ContaAzulProperties();
        p.setClientId("id");
        p.setClientSecret("secret");
        p.setRedirectUri("https://example.com/callback");
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ContaAzulTokenClient(p, builder.build());
    }

    void expect(String grant, String field, String value) {
        server.expect(requestTo(ContaAzulTokenClient.ENDPOINT)).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Basic aWQ6c2VjcmV0")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(content().formDataContains(java.util.Map.of("grant_type", grant, field, value))).andRespond(withSuccess("{\"access_token\":\"access-new\",\"refresh_token\":\"refresh-new\",\"expires_in\":3600,\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void exchangeUsesOfficialProtocol() {
        expect("authorization_code", "code", "code-value");
        assertEquals("refresh-new", client.trocar("code-value").refresh());
        server.verify();
    }

    @Test
    void refreshReturnsRotatedToken() {
        expect("refresh_token", "refresh_token", "refresh-old");
        assertEquals("refresh-new", client.renovar("refresh-old").refresh());
        server.verify();
    }

    @Test
    void providerErrorDoesNotExposeBody() {
        server.expect(requestTo(ContaAzulTokenClient.ENDPOINT)).andRespond(withBadRequest().body("private credential"));
        var e = assertThrows(IllegalStateException.class, () -> client.trocar("code"));
        assertFalse(e.getMessage().contains("private credential"));
        assertNull(e.getCause());
        server.verify();
    }

    @Test
    void malformedTokensRejected() {
        server.expect(requestTo(ContaAzulTokenClient.ENDPOINT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> client.trocar("code"));
        server.verify();
    }
}
