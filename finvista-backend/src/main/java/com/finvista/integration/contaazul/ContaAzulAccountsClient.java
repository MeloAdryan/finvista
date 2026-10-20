package com.finvista.integration.contaazul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ContaAzulAccountsClient {

    private static final String BASE = "https://api-v2.contaazul.com/v1/conta-financeira";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Conta(String id, String nome, String tipo, Boolean ativo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagina(@JsonProperty("itens_totais") Integer total, List<Conta> itens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Saldo(@JsonProperty("saldo_atual") BigDecimal valor) {}
    private final RestClient http;

    @Autowired
    public ContaAzulAccountsClient() {
        this(criarHttp());
    }

    public ContaAzulAccountsClient(RestClient http) {
        this.http = http;
    }

    private static RestClient criarHttp() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory).build();
    }

    public List<Conta> listar(String acesso) {
        try {
            var todas = new ArrayList<Conta>();
            var ids = new HashSet<String>();
            Integer esperado = null;
            for (int pagina = 1; pagina <= 1000; pagina++) {
                var p = http.get().uri(BASE + "?pagina=" + pagina + "&tamanho_pagina=1000&apenas_ativo=true&mostrar_caixinha=true")
                        .headers(h -> h.setBearerAuth(acesso)).retrieve().body(Pagina.class);
                if (p == null || p.total() == null || p.total() < 0 || p.itens() == null
                        || p.itens().size() > 1000 || p.itens().size() > p.total()) {
                    falhar("RESPOSTA_INVALIDA", "LISTAGEM");
                }
                if (p.total() > 1000) {
                    falhar("LIMITE_CONTAS", "LISTAGEM");
                }
                if (esperado != null && !esperado.equals(p.total())) {
                    falhar("LISTAGEM_MUDOU", "LISTAGEM");
                }
                esperado = p.total();
                if (p.itens().isEmpty() && todas.size() < esperado) {
                    falhar("LISTA_INCOMPLETA", "LISTAGEM");
                }
                for (var c : p.itens()) {
                    if (c == null || c.id() == null || !ids.add(c.id()) || c.nome() == null || c.nome().isBlank()
                            || c.tipo() == null || c.tipo().isBlank() || !Boolean.TRUE.equals(c.ativo())) {
                        falhar("CONTA_INVALIDA", "LISTAGEM");
                    }
                    try {
                        UUID.fromString(c.id());
                    } catch (IllegalArgumentException e) {
                        falhar("CONTA_INVALIDA", "LISTAGEM");
                    }
                    todas.add(c);
                }
                if (todas.size() > esperado) {
                    falhar("RESPOSTA_INVALIDA", "LISTAGEM");
                }
                if (todas.size() == esperado) {
                    return List.copyOf(todas);
                }
            }
            throw new ContaAzulAccountsException("LISTA_INCOMPLETA", "LISTAGEM", null);
        } catch (ContaAzulAccountsException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new ContaAzulAccountsException("HTTP_EXTERNO", "LISTAGEM", e.getStatusCode().value());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new ContaAzulAccountsException("REDE_OU_TEMPO", "LISTAGEM", null);
        } catch (RuntimeException e) {
            throw new ContaAzulAccountsException("RESPOSTA_INVALIDA", "LISTAGEM", null);
        }
    }

    private static void falhar(String codigo, String etapa) {
        throw new ContaAzulAccountsException(codigo, etapa, null);
    }

    public BigDecimal saldo(String acesso, String id) {
        UUID.fromString(id);
        try {
            var saldo = http.get().uri(BASE + "/" + id + "/saldo-atual")
                    .headers(h -> h.setBearerAuth(acesso)).retrieve().body(Saldo.class);
            if (saldo == null || saldo.valor() == null) {
                falhar("SALDO_AUSENTE", "SALDO");
            }
            return saldo.valor();
        } catch (ContaAzulAccountsException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new ContaAzulAccountsException("HTTP_EXTERNO", "SALDO", e.getStatusCode().value());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new ContaAzulAccountsException("REDE_OU_TEMPO", "SALDO", null);
        } catch (RuntimeException e) {
            throw new ContaAzulAccountsException("RESPOSTA_INVALIDA", "SALDO", null);
        }
    }
}
