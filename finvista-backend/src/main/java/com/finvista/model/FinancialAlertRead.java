package com.finvista.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;

@Entity
@Table(name = "financial_alert_reads", uniqueConstraints = @UniqueConstraint(
        name = "uk_financial_alert_read_usuario", columnNames = {"alerta_id", "usuario_id"}))
public class FinancialAlertRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alerta_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FinancialAlertRecord alerta;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Usuario usuario;
    @Column(name = "lido_em", nullable = false)
    private Instant lidoEm;

    protected FinancialAlertRead() {
    }

    public FinancialAlertRead(FinancialAlertRecord alerta, Usuario usuario, Instant agora) {
        this.alerta = alerta;
        this.usuario = usuario;
        this.lidoEm = agora;
    }

    public void marcar(Instant agora) {
        lidoEm = agora;
    }

    public FinancialAlertRecord getAlerta() {
        return alerta;
    }

    public Instant getLidoEm() {
        return lidoEm;
    }
}
