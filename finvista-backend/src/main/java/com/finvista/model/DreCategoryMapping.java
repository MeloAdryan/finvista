package com.finvista.model;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="dre_mapeamentos",uniqueConstraints=@UniqueConstraint(name="uk_dre_cliente_categoria",columnNames={"cliente_id","categoria_chave"}))
public class DreCategoryMapping {
 @Id public UUID id;
 @Column(name="cliente_id",nullable=false) public Long clienteId;
 @Column(name="categoria_chave",nullable=false,length=300) public String categoriaChave;
 @Column(nullable=false,length=255) public String categoria;
 @Column(nullable=false,length=20) public String tipo;
 @Column(nullable=false,length=60) public String grupo;
 @Column(name="revisado_em",nullable=false) public Instant revisadoEm;
 @Column(name="revisado_por",nullable=false) public Long revisadoPor;
 @Column(length=1000) public String observacao;
 @Version public long versao;
}
