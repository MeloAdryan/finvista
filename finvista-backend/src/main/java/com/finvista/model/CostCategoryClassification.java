package com.finvista.model;

import jakarta.persistence.*;

@Entity
@Table(name = "classificacoes_custo", uniqueConstraints =
        @UniqueConstraint(name = "uk_classificacao_cliente_categoria", columnNames = {"cliente_id", "categoria_chave"}))
public class CostCategoryClassification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;
    @Column(name = "categoria_chave", nullable = false, length = 255)
    private String categoriaChave;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private CostBehavior comportamento = CostBehavior.NAO_CLASSIFICADO;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private CostNature natureza = CostNature.NAO_CLASSIFICADO;

    public CostCategoryClassification() {}
    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long valor) { clienteId = valor; }
    public String getCategoriaChave() { return categoriaChave; }
    public void setCategoriaChave(String valor) { categoriaChave = valor; }
    public CostBehavior getComportamento() { return comportamento; }
    public void setComportamento(CostBehavior valor) { comportamento = valor; }
    public CostNature getNatureza() { return natureza; }
    public void setNatureza(CostNature valor) { natureza = valor; }
}