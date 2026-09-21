package com.finvista.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "metas_gastos")
public class SpendingGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(
            name = "data_inicio",
            nullable = false
    )
    private LocalDate dataInicio;

    @Column(
            name = "data_fim",
            nullable = false
    )
    private LocalDate dataFim;

    @Column(
            name = "valor_limite",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal valorLimite;

    @Column(
            name = "percentual_alerta",
            nullable = false
    )
    private Integer percentualAlerta;

    public SpendingGoal() {
    }

    public SpendingGoal(
            String tipo,
            LocalDate dataInicio,
            LocalDate dataFim,
            BigDecimal valorLimite,
            Integer percentualAlerta
    ) {
        this.tipo = tipo;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.valorLimite = valorLimite;
        this.percentualAlerta = percentualAlerta;
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }

    public BigDecimal getValorLimite() {
        return valorLimite;
    }

    public void setValorLimite(
            BigDecimal valorLimite
    ) {
        this.valorLimite = valorLimite;
    }

    public Integer getPercentualAlerta() {
        return percentualAlerta;
    }

    public void setPercentualAlerta(
            Integer percentualAlerta
    ) {
        this.percentualAlerta =
                percentualAlerta;
    }
}