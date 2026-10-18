package com.finvista.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "conta_azul_auto_execucoes")
public class ContaAzulAutoRun {

    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "cliente_id")
    public Long clienteId;
    @Column(name = "autorizado_por")
    public Long autorizadoPor;
    @Column(name = "revisao")
    public UUID revisao;
    @Column(name = "inicio")
    public LocalDate inicio;
    @Column(name = "fim")
    public LocalDate fim;
    @Column(name = "incluir_novas")
    public boolean incluirNovas;
    @Column(name = "status")
    public String status;
    @Column(name = "criado_em")
    public Instant criadoEm;
    @Column(name = "concluido_em")
    public Instant concluidoEm;
    @Column(name = "etapa")
    public int etapa;
    @Column(name = "pagina")
    public int pagina;
    @Column(name = "total_esperado")
    public Integer totalEsperado;
    @Column(name = "listagem_mudou")
    public boolean listagemMudou;
    @Column(name = "mensagem", length = 500)
    public String mensagem;
    @Column(name = "incluidas")
    public int incluidas;
    @Column(name = "atualizadas")
    public int atualizadas;
    @Column(name = "mantidas")
    public int mantidas;
    @Column(name = "pendentes")
    public int pendentes;
}
