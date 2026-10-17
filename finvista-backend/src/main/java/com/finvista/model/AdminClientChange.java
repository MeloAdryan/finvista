package com.finvista.model;

import jakarta.persistence.*;
import java.time.Instant;




@Entity
@Table(name = "admin_client_changes")
public class AdminClientChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(name = "usuario_nome", nullable = false, length = 120)
    private String usuarioNome;
    @Column(name = "nome_anterior", nullable = false, length = 150)
    private String nomeAnterior;
    @Column(name = "nome_novo", nullable = false, length = 150)
    private String nomeNovo;
    @Column(name = "ativo_anterior", nullable = false)
    private Boolean ativoAnterior;
    @Column(name = "ativo_novo", nullable = false)
    private Boolean ativoNovo;
    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    protected AdminClientChange() {
    }

    public AdminClientChange(Long clienteId, Usuario autor, String nomeAnterior,
            String nomeNovo, Boolean ativoAnterior, Boolean ativoNovo) {
        this.clienteId = clienteId;
        this.usuarioId = autor.getId();
        this.usuarioNome = autor.getNome();
        this.nomeAnterior = nomeAnterior;
        this.nomeNovo = nomeNovo;
        this.ativoAnterior = ativoAnterior;
        this.ativoNovo = ativoNovo;
        this.registradoEm = Instant.now();
    }

    public Long getClienteId() {
        return clienteId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getUsuarioNome() {
        return usuarioNome;
    }

    public String getNomeAnterior() {
        return nomeAnterior;
    }

    public String getNomeNovo() {
        return nomeNovo;
    }

    public Boolean getAtivoAnterior() {
        return ativoAnterior;
    }

    public Boolean getAtivoNovo() {
        return ativoNovo;
    }

    public Instant getRegistradoEm() {
        return registradoEm;
    }
}
