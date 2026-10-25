package com.finvista.integration.contaazul;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContaAzulMovementClient {

    public record Pagina(int total, List<JsonNode> itens, JsonNode fonte) {}
    private final RestClient http;

    @Autowired
    public ContaAzulMovementClient() {
        this(criar());
    }

    public ContaAzulMovementClient(RestClient http) {
        this.http = http;
    }

    private static RestClient criar() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(20000);
        return RestClient.builder().requestFactory(f).build();
    }

    public Pagina consultar(String acesso, String tipo, LocalDate inicio, LocalDate fim, int pagina) {
        if (pagina < 1 || inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new IllegalArgumentException("Cursor inválido.");
        }
        boolean transfer = "TRANSFERENCIA".equals(tipo);
        if (!transfer && !"SALDO_INICIAL".equals(tipo)) {
            throw new IllegalArgumentException("Fonte desconhecida.");
        }
        String path = transfer ? "/v1/financeiro/transferencias" : "/v1/financeiro/eventos-financeiros/saldo-inicial";
        var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(ContaAzulFinancialClient.BASE + path)
                .queryParam("pagina", pagina).queryParam("tamanho_pagina", 100)
                .queryParam("data_inicio", transfer ? inicio.toString() : inicio + "T00:00:00")
                .queryParam("data_fim", transfer ? fim.toString() : fim + "T23:59:59").build().toUri();
        try {
            JsonNode body = http.get().uri(uri).headers(h -> h.setBearerAuth(acesso)).retrieve().body(JsonNode.class);
            return validar(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar " + tipo + ". A etapa não foi considerada concluída.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida de " + tipo + ". A sincronização permanece incompleta.");
        }
    }

    static Pagina validar(JsonNode body) {
        if (body == null || !body.path("itens_totais").canConvertToInt() || !body.path("itens_totais").isIntegralNumber()
                || body.path("itens_totais").intValue() < 0 || !body.path("itens").isArray() || body.path("itens").size() > 100) {
            throw new IllegalArgumentException("Página da fonte inválida.");
        }
        List<JsonNode> rows = new ArrayList<>();
        for (var item : body.path("itens")) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("Registro da fonte inválido.");

            }
            rows.add(item);
        }
        if (rows.size() > body.path("itens_totais").intValue()) {
            throw new IllegalArgumentException("Contagem inválida.");
        }
        return new Pagina(body.path("itens_totais").intValue(), List.copyOf(rows), body);
    }
}
