package com.finvista.integration.contaazul;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Service
public class ContaAzulOAuthService {

    private static final String SCOPE =
            "openid profile aws.cognito.signin.user.admin";

    private final ContaAzulProperties properties;

    public ContaAzulOAuthService(ContaAzulProperties properties) {
        this.properties = properties;
    }

    public String gerarUrlAutorizacao(String redirectUri, String state) {

        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri é obrigatório.");
        }

        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state é obrigatório.");
        }

        String clientId = UriUtils.encodeQueryParam(
                properties.getClientId(),
                StandardCharsets.UTF_8
        );

        String redirectUriEncoded = UriUtils.encodeQueryParam(
                redirectUri,
                StandardCharsets.UTF_8
        );

        String stateEncoded = UriUtils.encodeQueryParam(
                state,
                StandardCharsets.UTF_8
        );

        String scopeEncoded = UriUtils.encodeQueryParam(
                SCOPE,
                StandardCharsets.UTF_8
        );

        return properties.getAuthorizationUrl()
                + "?response_type=code"
                + "&client_id=" + clientId
                + "&redirect_uri=" + redirectUriEncoded
                + "&state=" + stateEncoded
                + "&scope=" + scopeEncoded;
    }
}