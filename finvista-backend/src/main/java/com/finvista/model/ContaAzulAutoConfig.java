package com.finvista.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@ Entity 

    @Table(name = "conta_azul_auto_config")
    public class ContaAzulAutoConfig {

        @Column(name = "sincronizacao_completa")
        public Boolean sincronizacaoCompleta;

        @Id
        @Column(name = "cliente_id")
        public Long clienteId;
        @Column(name = "habilitada")
        public boolean habilitada;
        @Column(name = "incluir_novas")
        public boolean incluirNovas;
        @Column(name = "periodo_movel")
        public Boolean periodoMovel;
        @Column(name = "inicio")
        public LocalDate inicio;
        @Column(name = "fim")
        public LocalDate fim;
        @Column(name = "intervalo_minutos")
        public int intervaloMinutos;
        @Column(name = "autorizado_por")
        public Long autorizadoPor;
        @Column(name = "revisao")
        public UUID revisao;
        @Column(name = "alterado_em")
        public Instant alteradoEm;
        @Column(name = "proxima_em")
        public Instant proximaEm;
        @Column(name = "execucao_id")
        public UUID execucaoId;
        @Column(name = "dono")
        public UUID dono;
        @Column(name = "reserva_ate")
        public Instant reservaAte;
        @Column(name = "falhas")
        public int falhas;
        @Version
        @Column(name = "versao")
        public Long versao;
    }
