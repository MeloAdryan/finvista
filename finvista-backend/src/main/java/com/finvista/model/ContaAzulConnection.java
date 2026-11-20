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
        status = "ATIVA";
        ultimoErro = null;
        accessCipher = access;
        if (refreshCipher != null && !java.util.Objects.equals(refreshCipher, refresh)) previousRefreshCipher = refreshCipher;
        refreshCipher = refresh;
        expiresAt = expires;
        updatedAt = now;
    }

    @Column(name = "status", length = 24)
    private String status;
    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    public String getStatus() { return status == null ? "ATIVA" : status; }
    public String getUltimoErro() { return ultimoErro; }
    public void reautorizar(Instant now) {
        status = "REAUTORIZAR";
        ultimoErro = "A autorização expirou ou foi revogada. Autorize novamente esta empresa.";
        updatedAt = now;
    }


    @Column(name = "previous_refresh_cipher", columnDefinition = "text")
    private String previousRefreshCipher;
    public String getPreviousRefreshCipher() { return previousRefreshCipher; }


    @Column(name="empresa_externa_id", length=100) private String empresaExternaId;
    @Column(name="empresa_documento", length=30) private String empresaDocumento;
    @Column(name="empresa_razao_social", length=250) private String empresaRazaoSocial;
    @Column(name="empresa_verificada_em") private Instant empresaVerificadaEm;
    @Column(name="empresa_confirmada_em") private Instant empresaConfirmadaEm;
    @Column(name="empresa_confirmada_por") private Long empresaConfirmadaPor;
    public String getEmpresaExternaId() { return empresaExternaId; }
    public String getEmpresaDocumento() { return empresaDocumento; }
    public String getEmpresaRazaoSocial() { return empresaRazaoSocial; }
    public Instant getEmpresaVerificadaEm() { return empresaVerificadaEm; }
    public Instant getEmpresaConfirmadaEm() { return empresaConfirmadaEm; }
    public void identificar(String id, String documento, String nome, Instant now) {
        if (!java.util.Objects.equals(id, empresaExternaId) || !java.util.Objects.equals(documento, empresaDocumento)) {
            empresaConfirmadaEm = null; empresaConfirmadaPor = null;
        }
        empresaExternaId=id; empresaDocumento=documento; empresaRazaoSocial=nome; empresaVerificadaEm=now;
    }
    public void exigirConfirmacaoEmpresa() { empresaConfirmadaEm=null; empresaConfirmadaPor=null; empresaVerificadaEm=null; }
    public void confirmarEmpresa(Long usuario, Instant now) { empresaConfirmadaEm=now; empresaConfirmadaPor=usuario; }
}
