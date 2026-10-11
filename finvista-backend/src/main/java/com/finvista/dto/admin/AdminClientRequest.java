package com.finvista.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminClientRequest(@NotBlank
        @Size(max = 150)
        String nome) {}
