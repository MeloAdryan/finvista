package com.finvista.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lotes_conciliacao_conta_azul")
public class ContaAzulSyncBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false) private Cliente cliente;
    @Column(nullable = false, length = 64) private String hashArquivo;
    @Column(nullable = false, length = 64) private String tokenAprovado;
    @Column(nullable = false, length = 255) private String usuario;
    @Column(nullable = false) private Instant criadoEm;
    @Column(nullable = false) private LocalDate dataCorte;
    @Column(nullable = false, columnDefinition = "TEXT") private String auditoria;
    protected ContaAzulSyncBatch() {}
    public ContaAzulSyncBatch(Cliente cliente, String hashArquivo, String tokenAprovado,
            String usuario, LocalDate dataCorte, String auditoria) {
        this.cliente = cliente; this.hashArquivo = hashArquivo; this.tokenAprovado = tokenAprovado;
        this.usuario = usuario; this.criadoEm = Instant.now(); this.dataCorte = dataCorte;
        this.auditoria = auditoria;
    }
    public Long getId() { return id; }
    public String getAuditoria() { return auditoria; }
}
