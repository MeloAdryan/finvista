package com.finvista.dto.admin;

import jakarta.validation.constraints.*;


public record AdminClientUpdateRequest(
        @NotBlank
        @Size(max = 150)
        String nome,
        @NotNull
        Boolean ativo,
        @NotBlank
        @Size(max = 150)
        String nomeAnterior,
        @NotNull
        Boolean ativoAnterior) {}        