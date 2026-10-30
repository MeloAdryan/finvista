package com.finvista.dto.admin;

import jakarta.validation.constraints.*;

public record AdminDeleteRequest(@NotBlank
        String confirmacao, String revisao, String nomeAnterior, Boolean ativoAnterior) {}
