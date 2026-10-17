package com.finvista.integration.contaazul;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ContaAzulParcelDetailClientTest {

    static final String ID = "9986f173-f531-4660-96ae-04b71c879264";
    MockRestServiceServer server;
    ContaAzulParcelDetailClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ContaAzulParcelDetailClient(builder.build());
    }

    String url() {
        return ContaAzulFinancialClient.BASE + "/v1/financeiro/eventos-financeiros/parcelas/" + ID;
    }

    void response(String fields) {
        server.expect(requestTo(url())).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer private-token"))
                .andRespond(withSuccess("{\"id\":\"" + ID + "\"," + fields + "}", MediaType.APPLICATION_JSON));
    }

    ContaAzulParcelDetail query() {
        return client.consultar("private-token", ID);
    }

    @Test
    void readsNestedEventAndItsAmounts() {
        response("\"valor_total_liquido\":100.15,\"evento\":{\"tipo\":\"DESPESA\",\"condicao_pagamento\":{\"quantidade_parcelas\":3},\"rateio\":[{\"id_categoria\":\"cat-1\",\"valor\":300.45,\"valor_bruto\":310,\"rateio_centro_custo\":[{\"nome_centro_custo\":\"Administrativo\",\"valor\":300.45}]}]}");
        var result = query();
        assertEquals(new java.math.BigDecimal("100.15"), result.liquido());
        assertEquals(3, result.evento().condicao().quantidade());
        assertEquals(new java.math.BigDecimal("300.45"), result.evento().rateio().get(0).valor());
        assertEquals("Administrativo", result.evento().rateio().get(0).centros().get(0).centroNome());
        server.verify();
    }

    @Test
    void eventAmountIsNotReplacedWithInstallmentAmount() {
        response("\"valor_total_liquido\":50,\"evento\":{\"rateio\":[{\"valor\":1000}]}");
        var result = query();
        assertEquals(new java.math.BigDecimal("1000"), result.evento().rateio().get(0).valor());
        server.verify();
    }

    @Test
    void missingFieldsStayNull() {
        response("\"descricao\":\"Teste\"");
        var p = query();
        assertNull(p.liquido());
        assertNull(p.evento());
        server.verify();
    }

    @Test
    void nullCategoryRejected() {
        response("\"evento\":{\"rateio\":[null]}");
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void nullCostCenterRejected() {
        response("\"evento\":{\"rateio\":[{\"rateio_centro_custo\":[null]}]}");
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void emptyBodyRejected() {
        server.expect(requestTo(url())).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void otherIdRejected() {
        server.expect(requestTo(url())).andRespond(withSuccess("{\"id\":\"other\"}", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void missingIdRejected() {
        server.expect(requestTo(url())).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void malformedJsonRejected() {
        server.expect(requestTo(url())).andRespond(withSuccess("{broken", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void externalPrivateFieldsAreNotForwarded() throws Exception {
        response("\"conta_financeira\":{\"numero\":\"sensitive-account\"},\"nota\":\"private-note\"");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(query());
        assertFalse(json.contains("sensitive-account"));
        assertFalse(json.contains("private-note"));
        assertTrue(json.contains("valor_total_liquido"));
        server.verify();
    }

    void error(HttpStatus status, int expected) {
        server.expect(requestTo(url())).andRespond(withStatus(status).body("private-token"));
        var e = assertThrows(ResponseStatusException.class, this::query);
        assertEquals(expected, e.getStatusCode().value());
        assertFalse(e.getMessage().contains("private-token"));
        assertNull(e.getCause());
        server.verify();
    }

    @Test
    void missingParcelIs404() {
        error(HttpStatus.NOT_FOUND, 404);
    }

    @Test
    void providerLimitIs429() {
        error(HttpStatus.TOO_MANY_REQUESTS, 429);
    }

    @Test
    void revokedAccessIsSanitized() {
        error(HttpStatus.UNAUTHORIZED, 502);
    }

    @Test
    void serverErrorIsSanitized() {
        error(HttpStatus.INTERNAL_SERVER_ERROR, 502);
    }
}
