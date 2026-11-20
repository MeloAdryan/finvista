package com.finvista.integration.contaazul;

import com.fasterxml.jackson.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class ContaAzulCompanyClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Empresa(@JsonProperty("id_empresa") String id, String documento,
            @JsonProperty("razao_social") String nome) {}
    private final RestClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public ContaAzulCompanyClient(ContaAzulHttpTransport transport) {
        this(transport.client());
    }

    public ContaAzulCompanyClient(RestClient http) {
        this.http = http;
    }

    public Empresa consultar(String token) {
        try {
            var e = http.get().uri(ContaAzulFinancialClient.BASE + "/v1/pessoas/conta-conectada")
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(Empresa.class);
            if (e == null || e.id() == null || e.id().isBlank() || e.id().length() > 100
                    || e.documento() == null || !e.documento().replaceAll("[^0-9]", "").matches("[0-9]{14}")
                    || e.nome() == null || e.nome().isBlank() || e.nome().length() > 250) {
                throw new IllegalArgumentException();
            }
            return new Empresa(e.id(), e.documento().replaceAll("[^0-9]", ""), e.nome());
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                    "Não foi possível verificar a empresa no Conta Azul. Tente novamente.", ContaAzulAutoDiagnostic.causaSegura(e));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identificação da empresa indisponível. Nenhum vínculo foi confirmado.");
        }
    }
}
