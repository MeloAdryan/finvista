package com.finvista.dto.admin;
import jakarta.validation.constraints.*;
public record AdminUserUpdateRequest(@NotBlank @Size(max=120) String nome,
 @NotBlank @Email @Size(max=180) String email,@NotNull Boolean ativo,
 @NotBlank String revisao,String novaSenha,String confirmarSenha) {}
