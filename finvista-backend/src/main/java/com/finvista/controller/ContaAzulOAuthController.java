package com.finvista.controller;

import com.finvista.integration.contaazul.ContaAzulOAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/integracoes/conta-azul")
public class ContaAzulOAuthController {

    private static final String REDIRECT_URI =
            "https://www.contaazul.com";

    private final ContaAzulOAuthService oauthService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ContaAzulOAuthController(ContaAzulOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @GetMapping("/autorizacao")
    public ResponseEntity<Map<String, String>> obterUrlAutorizacao() {

        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);

        String state = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(stateBytes);

        String url = oauthService.gerarUrlAutorizacao(
                REDIRECT_URI,
                state
        );

        return ResponseEntity.ok(
                Map.of(
                        "authorizationUrl", url,
                        "state", state
                )
        );
    }
}