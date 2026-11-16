package com.finvista.dto.admin;

public record AdminUserResponse(Long id, String nome, String email, String perfil, Long clienteId, String clienteNome, Boolean ativo, String revisao, java.util.List<Long> empresasAdicionais) {
    public AdminUserResponse(Long id, String nome, String email, String perfil, Long clienteId, String clienteNome, Boolean ativo, String revisao) {
        this(id, nome, email, perfil, clienteId, clienteNome, ativo, revisao, java.util.List.of());
    }

    public AdminUserResponse(Long id, String nome, String email, String perfil, Long clienteId, String clienteNome, Boolean ativo) {
        this(id, nome, email, perfil, clienteId, clienteNome, ativo, null, java.util.List.of());
    }
}
