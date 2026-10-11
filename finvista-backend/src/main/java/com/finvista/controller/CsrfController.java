package com.finvista.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    public record TokenResponse(String headerName, String token) {}

    @GetMapping("/csrf")
    public ResponseEntity<TokenResponse> token(CsrfToken token) {
        // Materializa o token fornecido pelo filtro, sem criar um token paralelo.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TokenResponse(token.getHeaderName(), token.getToken()));
    }
}
