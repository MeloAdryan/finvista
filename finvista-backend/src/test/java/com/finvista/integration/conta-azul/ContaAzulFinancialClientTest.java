package com.finvista.integration.contaazul;

import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ContaAzulFinancialClientTest {

    MockRestServiceServer server;
    ContaAzulFinancialClient client;
    final LocalDate inicio = LocalDate.of(2026, 10, 1), fim = LocalDate.of(2026, 10, 31);

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ContaAzulFinancialClient(builder.build());
    }

    void response(String json) {
        server.expect(requestTo(url("contas-a-pagar", 1))).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer private-token")).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    String url(String tipo, int pagina) {
        return ContaAzulFinancialClient.BASE + "/v1/financeiro/eventos-financeiros/" + tipo + "/buscar?pagina=" + pagina + "&tamanho_pagina=100&data_vencimento_de=2026-10-01&data_vencimento_ate=2026-10-31";
    }

    ContaAzulFinancialClient.Pagina query() {
        return client.consultar("private-token", ContaAzulFinancialClient.Tipo.DESPESA, inicio, fim, 1);
    }

    @Test
    void parsesMoneyAndClassifications() {
        response("{\"itens_totais\":1,\"itens\":[{\"id\":\"id-1\",\"data_vencimento\":\"2026-10-10\",\"total\":100.15,\"pago\":20,\"nao_pago\":80.15,\"categorias\":[{\"id\":\"c1\",\"nome\":\"Aluguel\"}],\"centros_custo\":[{\"id\":\"cc1\",\"nome\":\"Administrativo\"}]}]}");
        var item = query().itens().get(0);
        assertEquals(new java.math.BigDecimal("100.15"), item.total());
        assertEquals("Aluguel", item.categorias().get(0).nome());
        assertEquals("Administrativo", item.centros().get(0).nome());
        server.verify();
    }

    @Test
    void missingAmountsStayNull() {
        response("{\"itens_totais\":1,\"itens\":[{\"id\":\"id-1\"}]}");
        assertNull(query().itens().get(0).total());
        server.verify();
    }

    @Test
    void emptyPage() {
        response("{\"itens_totais\":0,\"itens\":[]}");
        assertEquals(0, query().totalItens());
        server.verify();
    }

    @Test
    void nextPageUsesSameDatesAndCorrectType() {
        server.expect(requestTo(url("contas-a-receber", 2))).andRespond(withSuccess("{\"itens_totais\":101,\"itens\":[{\"id\":\"id-101\"}]}", MediaType.APPLICATION_JSON));
        assertEquals(101, client.consultar("token", ContaAzulFinancialClient.Tipo.RECEITA, inicio, fim, 2).totalItens());
        server.verify();
    }

    @Test
    void missingItemsRejected() {
        response("{\"itens_totais\":0}");
        assertEquals(502, assertThrows(ResponseStatusException.class, this::query).getStatusCode().value());
        server.verify();
    }

    @Test
    void missingCountRejected() {
        response("{\"itens\":[]}");
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void repeatedIdsRejected() {
        response("{\"itens_totais\":2,\"itens\":[{\"id\":\"same\"},{\"id\":\"same\"}]}");
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void missingIdRejected() {
        response("{\"itens_totais\":1,\"itens\":[{}]}");
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void wrongDatesRejected() {
        response("{\"itens_totais\":1,\"itens\":[{\"id\":\"id-1\",\"data_vencimento\":\"2026-09-10\"}]}");
        assertThrows(ResponseStatusException.class, this::query);
        server.verify();
    }

    @Test
    void limitIsNotRetriedOrExposed() {
        server.expect(requestTo(url("contas-a-pagar", 1))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("private-token internal-error"));
        var e = assertThrows(ResponseStatusException.class, this::query);
        assertEquals(429, e.getStatusCode().value());
        assertFalse(e.getMessage().contains("private-token"));
        assertNull(e.getCause());
        server.verify();
    }

    @Test
    void expiredProviderAccessIsSanitized() {
        server.expect(requestTo(url("contas-a-pagar", 1))).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private-token"));
        var e = assertThrows(ResponseStatusException.class, this::query);
        assertEquals(502, e.getStatusCode().value());
        assertFalse(e.getMessage().contains("private-token"));
        server.verify();
    }
}
