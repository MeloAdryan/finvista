package com.finvista.integration.contaazul;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class ContaAzulMovementClientTest {

    MockRestServiceServer server;
    ContaAzulMovementClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ContaAzulMovementClient(builder.build());
    }

    @Test
    void transferenciasPreservamContaDestinoValorAssinadoECamposAdicionais() {
        server.expect(requestTo(ContaAzulFinancialClient.BASE + "/v1/financeiro/transferencias?pagina=2&tamanho_pagina=100&data_inicio=1900-01-01&data_fim=2200-12-31"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer teste"))
                .andRespond(withSuccess("{\"itens_totais\":101,\"itens\":[{\"id\":\"tr-101\",\"valor\":-100.25,\"conta_destino\":\"c2\",\"adicional\":true}]}", MediaType.APPLICATION_JSON));
        var p = client.consultar("teste", "TRANSFERENCIA", ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 2);
        assertEquals(101, p.total());
        assertEquals(-100.25, p.itens().get(0).path("valor").doubleValue());
        assertEquals("c2", p.itens().get(0).path("conta_destino").asText());
        assertTrue(p.itens().get(0).path("adicional").booleanValue());
        server.verify();
    }

    @Test
    void saldosIniciaisUsamEndpointProprioSemVirarReceita() {
        server.expect(requestTo(ContaAzulFinancialClient.BASE + "/v1/financeiro/contas-financeiras/saldo-inicial?pagina=1&tamanho_pagina=100&data_inicio=2026-01-01T00:00:00&data_fim=2026-12-31T23:59:59"))
                .andRespond(withSuccess("{\"itens_totais\":1,\"itens\":[{\"valor\":-50,\"id_conta_financeira\":\"c1\"}]}", MediaType.APPLICATION_JSON));
        var p = client.consultar("teste", "SALDO_INICIAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1);
        assertEquals(-50, p.itens().get(0).path("valor").intValue());
        server.verify();
    }

    @Test
    void respostaIncompletaNaoViraZero() {
        server.expect(anything()).andRespond(withSuccess("{\"itens\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> client.consultar("t", "TRANSFERENCIA", ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 1)).getStatusCode().value());
    }

    @Test
    void limiteDeRequisicoesPreservaStatus429() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> client.consultar("t", "TRANSFERENCIA", ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 1)).getStatusCode().value());
    }

    @Test
    void preservaHttpOriginalSemExporCorpoNoDiagnostico() {
        for (var status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR)) {
            server.reset();
            server.expect(anything()).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"Bearer SECRET_NAO_EXIBIR\"}"));
            var erro = assertThrows(ResponseStatusException.class, () -> client.consultar("teste", "TRANSFERENCIA", ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 1));
            assertEquals(502, erro.getStatusCode().value());
            var origem = assertInstanceOf(org.springframework.web.client.RestClientResponseException.class, erro.getCause());
            assertEquals(status.value(), origem.getStatusCode().value());
            var resumo = ContaAzulAutoDiagnostic.resumir(erro);
            assertTrue(resumo.contains("http=" + status.value()));
            assertFalse(resumo.contains("SECRET_NAO_EXIBIR"));
            assertFalse(erro.getReason().contains("SECRET_NAO_EXIBIR"));
            server.verify();
        }
    }

    @Test
    void preservaCausaDaRespostaInvalidaSemConsiderarEtapaConcluida() {
        server.expect(anything()).andRespond(withSuccess("{\"itens\":[]}", MediaType.APPLICATION_JSON));
        var erro = assertThrows(ResponseStatusException.class, () -> client.consultar("teste", "TRANSFERENCIA", ContaAzulFullScope.INICIO, ContaAzulFullScope.FIM, 1));
        assertEquals(502, erro.getStatusCode().value());
        assertInstanceOf(IllegalArgumentException.class, erro.getCause());
        server.verify();
    }
}
