package com.finvista.integration.contaazul;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

@Service
public class ContaAzulOAuthService {

    private final ContaAzulProperties p;

    public ContaAzulOAuthService(ContaAzulProperties p) {
        this.p = p;
    }

    private String enc(String v) {
        return UriUtils.encodeQueryParam(v, StandardCharsets.UTF_8);
    }

    public String gerarUrlAutorizacao(String redirectUri, String state) {
        if (redirectUri == null || redirectUri.isBlank() || state == null || state.isBlank()) {
            throw new IllegalArgumentException("Callback e state são obrigatórios.");

        }
        return "https://login.contaazul.com/#/oauth/authorize?response_type=code&client_id=" + enc(p.getClientId()) + "&redirect_uri=" + enc(redirectUri) + "&state=" + enc(state) + "&scope=" + enc("openid profile aws.cognito.signin.user.admin");
    }
}
