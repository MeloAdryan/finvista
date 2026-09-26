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
@Table(name = "orcamentos")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "centro_custo", length = 150)
    private String centroCusto;

    @Column(length = 150)
    private String categoria;

    @Column(
            name = "valor_planejado",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal valorPlanejado;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    public Budget() {
    }

    public Budget(
            String nome,
            String centroCusto,
            String categoria,
            BigDecimal valorPlanejado,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        this.nome = nome;
        this.centroCusto = centroCusto;
        this.categoria = categoria;
        this.valorPlanejado = valorPlanejado;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCentroCusto() {
        return centroCusto;
    }

    public void setCentroCusto(String centroCusto) {
        this.centroCusto = centroCusto;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getValorPlanejado() {
        return valorPlanejado;
    }

    public void setValorPlanejado(
            BigDecimal valorPlanejado
    ) {
        this.valorPlanejado = valorPlanejado;
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
}