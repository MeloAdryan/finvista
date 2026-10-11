package com.finvista.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "conta_azul_connections")
public class ContaAzulConnection {

    @Id
    @Column(name = "cliente_id")
    private Long clienteId;
    @Version
    private Long versao;
    @Column(name = "access_cipher", nullable = false, columnDefinition = "text")
    private String accessCipher;
    @Column(name = "refresh_cipher", nullable = false, columnDefinition = "text")
    private String refreshCipher;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContaAzulConnection() {
    }

    public ContaAzulConnection(Long clienteId) {
        this.clienteId = clienteId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public String getAccessCipher() {
        return accessCipher;
    }

    public String getRefreshCipher() {
        return refreshCipher;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizar(String access, String refresh, Instant expires, Instant now) {
        accessCipher = access;
        refreshCipher = refresh;
        expiresAt = expires;
        updatedAt = now;
    }
}
