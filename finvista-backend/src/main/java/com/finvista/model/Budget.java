package com.finvista.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "orcamentos")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String cliente;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false)
    private Integer probabilidade;

    public Budget() {
    }

    public Budget(
            String cliente,
            BigDecimal valor,
            String status,
            Integer probabilidade
    ) {
        this.cliente = cliente;
        this.valor = valor;
        this.status = status;
        this.probabilidade = probabilidade;
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProbabilidade() {
        return probabilidade;
    }

    public void setProbabilidade(Integer probabilidade) {
        this.probabilidade = probabilidade;
    }
}