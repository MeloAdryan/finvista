package com.finvista.dto.admin;

public record AdminUserResponse(Long id, String nome, String email, String perfil, Long clienteId, String clienteNome, Boolean ativo) {}
