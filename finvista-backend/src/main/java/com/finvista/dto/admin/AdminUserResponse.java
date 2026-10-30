package com.finvista.dto.admin;

public record AdminUserResponse(Long id, String nome, String email, String perfil, Long clienteId, String clienteNome, Boolean ativo, String revisao) {

    public AdminUserResponse(Long id, String nome, String email, String perfil, Long clienteId, String clienteNome, Boolean ativo) {
        this(id, nome, email, perfil, clienteId, clienteNome, ativo, null);
    }
}
