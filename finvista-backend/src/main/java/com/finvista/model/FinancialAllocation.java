package com.finvista.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "rateios_financeiros", uniqueConstraints
        = @UniqueConstraint(name = "uk_rateio_bloco", columnNames = {"lancamento_id", "bloco"}))
public class FinancialAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lancamento_id", nullable = false)
    private FinancialTransaction lancamento;
    @Column(nullable = false)
    private int bloco;
    @Column(length = 255)
    private String categoria;
    @Column(name = "valor_categoria", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorCategoria;
    @Column(name = "centro_custo", length = 255)
    private String centro;
    @Column(name = "valor_centro", precision = 15, scale = 2)
    private BigDecimal valorCentro;

    protected FinancialAllocation() {
    }

    public FinancialAllocation(FinancialTransaction lancamento, int bloco, String categoria,
            BigDecimal valorCategoria, String centro, BigDecimal valorCentro) {
        this.lancamento = lancamento;
        this.bloco = bloco;
        this.categoria = categoria;
        this.valorCategoria = valorCategoria;
        this.centro = centro;
        this.valorCentro = valorCentro;
    }

    public FinancialTransaction getLancamento() {
        return lancamento;
    }

    public int getBloco() {
        return bloco;
    }

    public String getCategoria() {
        return categoria;
    }

    public BigDecimal getValorCategoria() {
        return valorCategoria;
    }

    public String getCentro() {
        return centro;
    }

    public BigDecimal getValorCentro() {
        return valorCentro;
    }
}


         
    
    
    
    
    
    
    
    
    
        
        
        
        
    
        
    
    
        
    
    
        
    
    
        
    
    
        
    
    
        
    