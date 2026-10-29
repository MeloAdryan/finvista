package com.finvista.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "conta_azul_auto_itens", indexes = @Index(name = "idx_ca_auto_fila", columnList = "execucao_id,status,id"))
public class ContaAzulAutoItem {

    @Column(name = "listada")
    public boolean listada;
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "execucao_id")
    public UUID execucaoId;
    @Column(name = "cliente_id")
    public Long clienteId;
    @Column(name = "parcela_id")
    public String parcelaId;
    @Column(name = "tipo")
    public String tipo;
    @Column(name = "descricao")
    public String descricao;
    @Column(name = "status")
    public String status;
    @Column(name = "mensagem", length = 500)
    public String mensagem;
    @Column(name = "lancamento_id")
    public Long lancamentoId;
    @Column(name = "concluido_em")
    public Instant concluidoEm;
}
