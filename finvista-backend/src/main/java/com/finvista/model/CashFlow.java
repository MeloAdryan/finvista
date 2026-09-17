package com.finvista.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "fluxo_caixa")
public class CashFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String periodo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal entradas;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saidas;

    public CashFlow() {
    }

    public CashFlow(
            String periodo,
            BigDecimal entradas,
            BigDecimal saidas
    ) {
        this.periodo = periodo;
        this.entradas = entradas;
        this.saidas = saidas;
    }

    public Long getId() {
        return id;
    }

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public BigDecimal getEntradas() {
        return entradas;
    }

    public void setEntradas(BigDecimal entradas) {
        this.entradas = entradas;
    }

    public BigDecimal getSaidas() {
        return saidas;
    }

    public void setSaidas(BigDecimal saidas) {
        this.saidas = saidas;
    }
}