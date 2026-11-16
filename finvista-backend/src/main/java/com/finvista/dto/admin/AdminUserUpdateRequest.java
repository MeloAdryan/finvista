package com.finvista.dto.admin;

import jakarta.validation.constraints.*;

public record AdminUserUpdateRequest(@NotBlank
        @Size(max = 120)
        String nome,
        @NotBlank
        @Email
        @Size(max = 180)
        String email, @NotNull
        Boolean ativo,
        @NotBlank
        String revisao, String novaSenha, String confirmarSenha,
        @Size(max = 100)
        java.util.List<@NotNull @Positive Long> empresasAdicionais) {

    public AdminUserUpdateRequest(String nome, String email, Boolean ativo,
            String revisao, String novaSenha, String confirmarSenha) {
        this(nome, email, ativo, revisao, novaSenha, confirmarSenha, null);
    }
}
