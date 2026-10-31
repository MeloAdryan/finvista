package com.finvista.model;

import jakarta.persistence.*;
import java.time.Instant;


@Entity
@Table(name = "extrato_movimentos", uniqueConstraints = @UniqueConstraint(
        name = "uk_extrato_cliente_chave_ocorrencia", columnNames = {"cliente_id", "chave", "ocorrencia"}))
public class StatementMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
    @Column(nullable = false, length = 64)
    private String chave;
    @Column(nullable = false)
    private int ocorrencia;
    @Column(nullable = false, columnDefinition = "text")
    private String conteudo;
    @Column(nullable = false, columnDefinition = "text")
    private String arquivo;
    @Column(name = "importado_em", nullable = false)
    private Instant importadoEm;
    protected StatementMovement() {}
    public StatementMovement(Cliente cliente, String chave, int ocorrencia, String conteudo, String arquivo, Instant agora) {
        this.cliente = cliente; this.chave = chave; this.ocorrencia = ocorrencia;
        this.conteudo = conteudo; this.arquivo = arquivo; this.importadoEm = agora;
    }
    public Instant getImportadoEm() { return importadoEm; }
    public Long getId() { return id; }
    public String getChave() { return chave; }
    public int getOcorrencia() { return ocorrencia; }
    public void registrarRevisaoHistorica(String conteudo) { this.conteudo = conteudo; }
    public String getConteudo() { return conteudo; }
    public String getArquivo() { return arquivo; }
}
