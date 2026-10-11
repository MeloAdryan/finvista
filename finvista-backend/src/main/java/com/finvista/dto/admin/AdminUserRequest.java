package com.finvista.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUserRequest(@NotBlank
        @Size(max = 120)
        String nome,
        @NotBlank
        @Email
        @Size(max = 180)
        String email,
        @NotBlank
        @Size(min = 8, max = 72)
        String senha,
        @NotBlank
        @Pattern(regexp = "ADMIN|CLIENTE")
        String perfil, Long clienteId) {}
