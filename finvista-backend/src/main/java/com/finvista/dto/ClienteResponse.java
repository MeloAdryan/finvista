package com.finvista.dto;

import java.time.LocalDateTime;

public record ClienteResponse(
        Long id,
        String nome,
        Boolean ativo,
        LocalDateTime dataCriacao,
        LocalDateTime dataModificacao
) {
    public ClienteResponse(Long id, String nome, Boolean ativo, LocalDateTime dataCriacao) {
        this(id, nome, ativo, dataCriacao, null);
    }
}
