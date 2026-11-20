package com.finvista.integration.contaazul;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class ContaAzulCompanyClientTest {
    MockRestServiceServer server;ContaAzulCompanyClient client;
    @BeforeEach void prepare(){var b=RestClient.builder();server=MockRestServiceServer.bindTo(b).build();client=new ContaAzulCompanyClient(b.build());}
    @Test void onlyMinimalCompanyFieldsAreReturned() throws Exception {
        server.expect(requestTo(ContaAzulFinancialClient.BASE+"/v1/pessoas/conta-conectada")).andExpect(header("Authorization","Bearer private-token"))
            .andRespond(withSuccess("{\"id_empresa\":\"123\",\"documento\":\"05.206.246/0001-38\",\"razao_social\":\"Empresa\",\"email\":\"privado@example.test\"}",MediaType.APPLICATION_JSON));
        var e=client.consultar("private-token");assertEquals("05206246000138",e.documento());
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(e).contains("privado"));server.verify();
    }
    @Test void missingIdentityIsRejected(){
        server.expect(anything()).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class,()->client.consultar("private-token"));server.verify();
    }
    @Test void errorsKeepSanitizedRetryInformation(){
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","120").body("private-token"));
        var e=assertThrows(ResponseStatusException.class,()->client.consultar("private-token"));
        assertTrue(ContaAzulAutoDiagnostic.http(e,429));assertNotNull(ContaAzulAutoDiagnostic.retryAfter(e));assertFalse(e.toString().contains("private-token"));server.verify();
    }
}
