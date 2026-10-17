package com.finvista.integration.contaazul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


@Component
public class ContaAzulFinancialClient {

    public static final String BASE = "https://api-v2.contaazul.com";
    public static final int TAMANHO = 100;

    public enum Tipo {
        RECEITA("contas-a-receber"), DESPESA("contas-a-pagar");
        final String segmento;

        Tipo(String segmento) {
            this.segmento = segmento;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Classificacao(String id, String nome) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parcela(String id, String descricao,
            @JsonProperty("data_vencimento") LocalDate vencimento,
            @JsonProperty("data_competencia") LocalDate competencia,
            String status, @JsonProperty("status_traduzido") String statusTraduzido,
            BigDecimal total, BigDecimal pago, @JsonProperty("nao_pago") BigDecimal pendente,
            List<Classificacao> categorias, @JsonProperty("centros_custo") List<Classificacao> centros) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagina(@JsonProperty("itens_totais") Integer totalItens, List<Parcela> itens) {}
    private final RestClient http;

    @Autowired
    public ContaAzulFinancialClient() {
        this(criarHttp());
    }

    public ContaAzulFinancialClient(RestClient http) {
        this.http = http;
    }

    private static RestClient criarHttp() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(20000);
        return RestClient.builder().requestFactory(f).build();
    }

    public Pagina consultar(String acesso, Tipo tipo, LocalDate inicio, LocalDate fim, int pagina) {
        try {
            String endpoint = BASE + "/v1/financeiro/eventos-financeiros/" + tipo.segmento + "/buscar";
            var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(endpoint)
                    .queryParam("pagina", pagina).queryParam("tamanho_pagina", TAMANHO)
                    .queryParam("data_vencimento_de", inicio).queryParam("data_vencimento_ate", fim)
                    .build().toUri();
            Pagina result = http.get().uri(uri).headers(h -> h.setBearerAuth(acesso)).retrieve().body(Pagina.class);
            validar(result, inicio, fim);
            return result;
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Conta Azul recusou o acesso. Renove ou autorize a conexão novamente.");
            }
            if (code == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de consultas da Conta Azul atingido. Aguarde antes de tentar novamente.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A Conta Azul não concluiu a consulta. Tente novamente mais tarde.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A consulta não retornou uma página válida. Nenhum lançamento foi importado.");
        }
    }

    private void validar(Pagina p, LocalDate inicio, LocalDate fim) {
        if (p == null || p.totalItens() == null || p.totalItens() < 0 || p.itens() == null || p.itens().size() > TAMANHO || p.itens().size() > p.totalItens()) {
            throw new IllegalArgumentException();
        } 
        var ids = new HashSet<String>();
        for (Parcela item : p.itens()) {
            if (item == null || item.id() == null || item.id().isBlank() || !ids.add(item.id())) {
                throw new IllegalArgumentException();
            }
            if (item.vencimento() != null && (item.vencimento().isBefore(inicio) || item.vencimento().isAfter(fim))) {
                throw new IllegalArgumentException();
            }
            if (item.categorias() != null && item.categorias().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException();
            }
            if (item.centros() != null && item.centros().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException();
            }
        }
    }
}
