package com.finvista.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Source archive, never an additional revenue or expense.
 */
@Entity
@Table(name = "conta_azul_fontes_capturadas", indexes = @Index(name = "idx_ca_fontes_cliente_execucao", columnList = "cliente_id,execucao_id,tipo"))
public class ContaAzulSourceCapture {

    @Id
    public UUID id;
    @Column(name = "cliente_id", nullable = false)
    public Long clienteId;
    @Column(name = "execucao_id", nullable = false)
    public UUID execucaoId;
    @Column(nullable = false, length = 30)
    public String tipo;
    @Column(nullable = false, length = 160)
    public String chave;
    @Column(nullable = false, columnDefinition = "text")
    public String conteudo;
    @Column(name = "capturado_em", nullable = false)
    public Instant capturadoEm;
}
