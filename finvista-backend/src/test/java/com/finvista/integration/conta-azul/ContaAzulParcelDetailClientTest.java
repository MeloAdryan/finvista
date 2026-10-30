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
    void nullCategoryPreservedForClassificationReview() {
        response("\"evento\":{\"rateio\":[null]}");
        assertNull(query().evento().rateio().get(0));
        server.verify();
    }

    @Test
    void nullCostCenterPreservedForClassificationReview() {
        response("\"evento\":{\"rateio\":[{\"rateio_centro_custo\":[null]}]}");
        assertNull(query().evento().rateio().get(0).centros().get(0));
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
    @Test
    void buscaBaixasQuandoParcelaPagaNaoAsInclui() {
        response("\"valor_pago\":10,\"nao_pago\":0");
        server.expect(requestTo(url()+"/baixa")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization","Bearer private-token"))
                .andRespond(withSuccess("[{\"id\":\"baixa\",\"id_parcela\":\""+ID+"\",\"data_pagamento\":\"2026-10-19\","
                        +"\"valor_composicao\":{\"valor_bruto\":10,\"juros\":0,\"multa\":0,\"desconto\":0,\"taxa\":0}}]",MediaType.APPLICATION_JSON));
        var p=query(); assertEquals(1,p.baixas().size());
        assertEquals(java.time.LocalDate.parse("2026-10-19"),p.baixas().get(0).data()); server.verify();
    }
    @Test
    void erroAoBuscarBaixasNaoRetornaDetalheFinanceiroIncompleto() {
        response("\"valor_pago\":10");
        server.expect(requestTo(url()+"/baixa")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(429,assertThrows(ResponseStatusException.class,this::query).getStatusCode().value()); server.verify();
    }
    @Test
    void listaVaziaEmDetalhePagoNaoDispensaConsultaDasBaixas() {
        response("\"valor_pago\":10,\"baixas\":[]");
        server.expect(requestTo(url()+"/baixa")).andRespond(withSuccess(
            "[{\"id\":\"baixa-real\",\"data_pagamento\":\"2026-10-19\",\"valor_composicao\":{\"valor_bruto\":10,\"juros\":0,\"multa\":0,\"desconto\":0,\"taxa\":0}}]", MediaType.APPLICATION_JSON));
        assertEquals("baixa-real",query().baixas().get(0).id()); server.verify();
    }
    @Test
    void listaEmbutidaParcialEsubstituidaPorHistoricoDaRotaEspecifica() {
        response("\"valor_pago\":30,\"baixas\":[{\"id\":\"antiga\"}]");
        server.expect(requestTo(url()+"/baixa")).andRespond(withSuccess(
            "[{\"id\":\"primeira\",\"data_pagamento\":\"2026-09-01\",\"valor_composicao\":{\"valor_bruto\":10}},{\"id\":\"segunda\",\"data_pagamento\":\"2026-10-01\",\"valor_composicao\":{\"valor_bruto\":20}}]",MediaType.APPLICATION_JSON));
        assertEquals(2,query().baixas().size());server.verify();
    }
    @Test
    void parcelaSemPagamentoNaoExigeConsultaAdicional() {
        response("\"valor_pago\":0,\"baixas\":[]");
        assertTrue(query().baixas().isEmpty());server.verify();
    }

    @Test
    void quitacao200SemLiquidoPrincipalPodeSerConferidaAposBuscarBaixas() {
        response("""
            "status":"QUITADO","valor_pago":200,"nao_pago":0,"baixas":[],
            "valor_composicao":{"valor_bruto":200,"valor_liquido":200,"juros":0,"multa":0,"desconto":0,"taxa":0},
            "evento":{"id":"evento","tipo":"RECEITA","data_competencia":"2026-08-26","condicao_pagamento":{"quantidade_parcelas":1}}
            """);
        server.expect(requestTo(url()+"/baixa")).andRespond(withSuccess("""
            [{"id":"pagamento-200","id_parcela":"9986f173-f531-4660-96ae-04b71c879264","data_pagamento":"2026-08-26",
              "valor_composicao":{"valor_bruto":200,"valor_liquido":200,"juros":0,"multa":0,"desconto":0,"taxa":0}}]
            """, MediaType.APPLICATION_JSON));
        var result=client.consultarCompleto("private-token",ID);
        assertEquals(0,new java.math.BigDecimal("200").compareTo(result.parcela().liquidoConferido()));
        assertEquals(0,new java.math.BigDecimal("200").compareTo(ContaAzulSettlementPolicy.conferir(result.parcela()).totalPago()));
        assertEquals("pagamento-200",result.fonte().path("baixas").get(0).path("id").asText());
        server.verify();
    }


    @Test
    void baixaEmbutidaComPagoZeradoConsultaRemocaoNaFonte() {
        response("\"valor_pago\":0,\"baixas\":[{\"id\":\"antiga\"}]");
        server.expect(requestTo(url()+"/baixa")).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
        assertTrue(query().baixas().isEmpty());server.verify();
    }

    @Test
    void contaDaBaixaPreservaIdTextoOuObjetoSemDadosBancariosPrivados() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        for (String conta : java.util.List.of("\"conta-a\"", "{\"id\":\"conta-a\",\"nome\":\"Banco A\",\"numero\":\"sensitive-account\"}")) {
            var baixa = mapper.readValue("{\"id\":\"b\",\"conta_financeira\":"+conta+"}",ContaAzulParcelDetail.Baixa.class);
            assertTrue(mapper.writeValueAsString(baixa).contains("conta-a"));
            assertFalse(mapper.writeValueAsString(baixa).contains("sensitive-account"));
        }
    }

    @Test
    void contratoComContasEAjustesPodeSerRelidoDaAuditoria() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var original = new ContaAzulParcelDetail(ID,"Teste","PENDENTE",java.time.LocalDate.of(2026,10,21),null,java.math.BigDecimal.ZERO,java.math.BigDecimal.TEN,
                new ContaAzulParcelDetail.Composicao(java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO),null);
        assertEquals(original,mapper.readValue(mapper.writeValueAsString(original),ContaAzulParcelDetail.class));
    }
}
