package com.finvista.dto.auth;

public record LoginRequest(
        String email,
        String senha
) {
}