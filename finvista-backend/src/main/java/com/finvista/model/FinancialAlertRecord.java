package com.finvista.model;

import com.finvista.dto.FinancialAlertsResponse.Alerta;
import com.finvista.dto.FinancialAlertsResponse.Gravidade;
import com.finvista.dto.FinancialAlertsResponse.Tipo;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "financial_alert_records", uniqueConstraints = @UniqueConstraint(
        name = "uk_financial_alert_cliente_chave", columnNames = {"cliente_id", "chave"}),
        indexes = @Index(name = "idx_financial_alert_cliente_periodo", columnList = "cliente_id,data_inicio,data_fim"))
public class FinancialAlertRecord {

    public enum Estado {
        ATIVO, RESOLVIDO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Cliente cliente;
    @Column(nullable = false, length = 600)
    private String chave;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Tipo tipo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gravidade gravidade;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;
    @Column(nullable = false)
    private int prioridade;
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;
    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;
    @Column(nullable = false, columnDefinition = "text")
    private String dados;
    @Column(nullable = false)
    private int ocorrencias;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "alterado_em", nullable = false)
    private Instant alteradoEm;
    @Column(name = "verificado_em", nullable = false)
    private Instant verificadoEm;
    @Column(name = "notificado_em", nullable = false)
    private Instant notificadoEm;
    @Column(name = "resolvido_em")
    private Instant resolvidoEm;
    @Version
    private Long versao;

    protected FinancialAlertRecord() {
    }

    public FinancialAlertRecord(Cliente cliente, Alerta alerta, String dados, Instant agora) {
        this.cliente = cliente;
        this.chave = alerta.id();
        this.ocorrencias = 1;
        this.criadoEm = agora;
        this.notificadoEm = agora;
        this.alteradoEm = agora;
        this.estado = Estado.ATIVO;
        atualizar(alerta, dados, agora);
    }

    public void atualizar(Alerta alerta, String dados, Instant agora) {
        boolean reativado = estado == Estado.RESOLVIDO;
        boolean agravado = gravidade != null && alerta.gravidade().ordinal() > gravidade.ordinal();
        if (reativado) {
            ocorrencias++;
        }
        if (reativado || agravado) {
            notificadoEm = agora;
        }
        if (reativado || !dados.equals(this.dados)) {
            alteradoEm = agora;
        }
        this.tipo = alerta.tipo();
        this.gravidade = alerta.gravidade();
        this.prioridade = alerta.gravidade().ordinal();
        this.estado = Estado.ATIVO;
        this.dataInicio = alerta.inicio();
        this.dataFim = alerta.fim();
        this.dados = dados;
        this.verificadoEm = agora;
        this.resolvidoEm = null;
    }

    public void resolver(Instant agora) {
        if (estado != Estado.RESOLVIDO) {
            estado = Estado.RESOLVIDO;
            resolvidoEm = agora;
            alteradoEm = agora;
        }
        verificadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getChave() {
        return chave;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public Gravidade getGravidade() {
        return gravidade;
    }

    public Estado getEstado() {
        return estado;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public String getDados() {
        return dados;
    }

    public int getOcorrencias() {
        return ocorrencias;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAlteradoEm() {
        return alteradoEm;
    }

    public Instant getVerificadoEm() {
        return verificadoEm;
    }

    public Instant getNotificadoEm() {
        return notificadoEm;
    }

    public Instant getResolvidoEm() {
        return resolvidoEm;
    }
}
