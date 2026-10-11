package com.finvista.integration.contaazul;

import com.fasterxml.jackson.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class ContaAzulTokenClient {

    public static final String ENDPOINT = "https://api-v2.contaazul.com/oauth/token";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tokens(@JsonProperty("access_token") String access, @JsonProperty("refresh_token") String refresh, @JsonProperty("expires_in") long expires, @JsonProperty("token_type") String tipo) {

        @Override
        public String toString() {
            return "Tokens[credenciais omitidas]";
        }
    }
    private final ContaAzulProperties p;
    private final RestClient http;

    @Autowired
    public ContaAzulTokenClient(ContaAzulProperties p) {
        this(p, criarHttp());
    }

    public ContaAzulTokenClient(ContaAzulProperties p, RestClient http) {
        this.p = p;
        this.http = http;
    }

    private static RestClient criarHttp() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(20000);
        return RestClient.builder().requestFactory(f).build();
    }

    public Tokens trocar(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", p.getRedirectUri());
        return enviar(form);
    }

    public Tokens renovar(String refresh) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refresh);
        return enviar(form);
    }

    private Tokens enviar(LinkedMultiValueMap<String, String> form) {
        try {
            Tokens t = http.post().uri(ENDPOINT).headers(h -> h.setBasicAuth(p.getClientId(), p.getClientSecret())).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Tokens.class);
            if (t == null || t.access() == null || t.access().isBlank() || t.refresh() == null || t.refresh().isBlank() || t.expires() <= 0 || t.expires() > 86400 || !"Bearer".equalsIgnoreCase(t.tipo())) {
                throw new IllegalArgumentException();
            
            }return t;
        } catch (Exception e) {
            throw new IllegalStateException("A Conta Azul não confirmou as credenciais. Tente autorizar novamente; nenhum token é exibido nesta resposta.");
        }
    }
}
