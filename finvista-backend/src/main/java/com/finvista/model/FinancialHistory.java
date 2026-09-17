package com.finvista.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "historico_financeiro")
public class FinancialHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String periodo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal receita;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal despesa;

    public FinancialHistory() {
    }

    public FinancialHistory(
            String periodo,
            BigDecimal receita,
            BigDecimal despesa
    ) {
        this.periodo = periodo;
        this.receita = receita;
        this.despesa = despesa;
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

    public BigDecimal getReceita() {
        return receita;
    }

    public void setReceita(BigDecimal receita) {
        this.receita = receita;
    }

    public BigDecimal getDespesa() {
        return despesa;
    }

    public void setDespesa(BigDecimal despesa) {
        this.despesa = despesa;
    }
}