package com.finvista.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity

@Table(name = "lancamentos_financeiros")
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "data_competencia")
    private LocalDate dataCompetencia;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "data_prevista")
    private LocalDate dataPrevista;

    @Column(name = "data_realizacao")
    private LocalDate dataRealizacao;

    @Column(nullable = false, length = 255)
    private String descricao;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "valor_original", precision = 15, scale = 2)
    private BigDecimal valorOriginal;

    @Column(name = "valor_realizado", precision = 15, scale = 2)
    private BigDecimal valorRealizado;

    @Column(name = "valor_aberto", precision = 15, scale = 2)
    private BigDecimal valorAberto;

    @Column(name = "juros_realizado", precision = 15, scale = 2)
    private BigDecimal jurosRealizado;

    @Column(name = "multa_realizada", precision = 15, scale = 2)
    private BigDecimal multaRealizada;

    @Column(name = "desconto_realizado", precision = 15, scale = 2)
    private BigDecimal descontoRealizado;

    @Column(name = "juros_previsto", precision = 15, scale = 2)
    private BigDecimal jurosPrevisto;

    @Column(name = "multa_prevista", precision = 15, scale = 2)
    private BigDecimal multaPrevista;

    @Column(name = "desconto_previsto", precision = 15, scale = 2)
    private BigDecimal descontoPrevisto;

    @Column(name = "valor_total_realizado", precision = 15, scale = 2)
    private BigDecimal valorTotalRealizado;

    @Column(name = "valor_total_aberto", precision = 15, scale = 2)
    private BigDecimal valorTotalAberto;

    @Column(length = 255)
    private String categoria;

    @Column(name = "centro_custo", length = 255)
    private String centroCusto;

    @Column(length = 100)
    private String origem;

    @Column(name = "documento_referencia", length = 255)
    private String documentoReferencia;

    public FinancialTransaction() {
    }

    public FinancialTransaction(
            LocalDate data,
            String descricao,
            String tipo,
            BigDecimal valor,
            String categoria,
            String centroCusto,
            String origem,
            String documentoReferencia
    ) {
        this.data = data;
        this.descricao = descricao;
        this.tipo = tipo;
        this.valor = valor;
        this.categoria = categoria;
        this.centroCusto = centroCusto;
        this.origem = origem;
        this.documentoReferencia = documentoReferencia;
    }
    @Column(name = "entidade_externa_id", length = 255)
    private String entidadeExternaId;

    @Column(name = "entidade_nome", length = 255)
    private String entidadeNome;

    @Column(name = "codigo_referencia", length = 255)
    private String codigoReferencia;

    @Column(length = 100)
    private String situacao;

    @Column(length = 100)
    private String recorrencia;

    @Column(name = "quantidade_recorrencia", length = 50)
    private String quantidadeRecorrencia;

    @Column
    private Boolean agendado;

    @Column(name = "forma_movimentacao", length = 255)
    private String formaMovimentacao;

    @Column(name = "conta_bancaria", length = 255)
    private String contaBancaria;

    @Column(name = "nota_fiscal", length = 255)
    private String notaFiscal;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    public LocalDate getDataCompetencia() {
        return dataCompetencia;
    }

    public void setDataCompetencia(LocalDate dataCompetencia) {
        this.dataCompetencia = dataCompetencia;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(LocalDate dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public LocalDate getDataPrevista() {
        return dataPrevista;
    }

    public void setDataPrevista(LocalDate dataPrevista) {
        this.dataPrevista = dataPrevista;
    }

    public LocalDate getDataRealizacao() {
        return dataRealizacao;
    }

    public void setDataRealizacao(LocalDate dataRealizacao) {
        this.dataRealizacao = dataRealizacao;
    }

    public BigDecimal getValorOriginal() {
        return valorOriginal;
    }

    public void setValorOriginal(BigDecimal valorOriginal) {
        this.valorOriginal = valorOriginal;
    }

    public BigDecimal getValorRealizado() {
        return valorRealizado;
    }

    public void setValorRealizado(BigDecimal valorRealizado) {
        this.valorRealizado = valorRealizado;
    }

    public BigDecimal getValorAberto() {
        return valorAberto;
    }

    public void setValorAberto(BigDecimal valorAberto) {
        this.valorAberto = valorAberto;
    }

    public BigDecimal getJurosRealizado() {
        return jurosRealizado;
    }

    public void setJurosRealizado(BigDecimal jurosRealizado) {
        this.jurosRealizado = jurosRealizado;
    }

    public BigDecimal getMultaRealizada() {
        return multaRealizada;
    }

    public void setMultaRealizada(BigDecimal multaRealizada) {
        this.multaRealizada = multaRealizada;
    }

    public BigDecimal getDescontoRealizado() {
        return descontoRealizado;
    }

    public void setDescontoRealizado(BigDecimal descontoRealizado) {
        this.descontoRealizado = descontoRealizado;
    }

    public BigDecimal getJurosPrevisto() {
        return jurosPrevisto;
    }

    public void setJurosPrevisto(BigDecimal jurosPrevisto) {
        this.jurosPrevisto = jurosPrevisto;
    }

    public BigDecimal getMultaPrevista() {
        return multaPrevista;
    }

    public void setMultaPrevista(BigDecimal multaPrevista) {
        this.multaPrevista = multaPrevista;
    }

    public BigDecimal getDescontoPrevisto() {
        return descontoPrevisto;
    }

    public void setDescontoPrevisto(BigDecimal descontoPrevisto) {
        this.descontoPrevisto = descontoPrevisto;
    }

    public BigDecimal getValorTotalRealizado() {
        return valorTotalRealizado;
    }

    public void setValorTotalRealizado(BigDecimal valorTotalRealizado) {
        this.valorTotalRealizado = valorTotalRealizado;
    }

    public BigDecimal getValorTotalAberto() {
        return valorTotalAberto;
    }

    public void setValorTotalAberto(BigDecimal valorTotalAberto) {
        this.valorTotalAberto = valorTotalAberto;
    }

    public String getEntidadeExternaId() {
        return entidadeExternaId;
    }

    public void setEntidadeExternaId(String entidadeExternaId) {
        this.entidadeExternaId = entidadeExternaId;
    }

    public String getEntidadeNome() {
        return entidadeNome;
    }

    public void setEntidadeNome(String entidadeNome) {
        this.entidadeNome = entidadeNome;
    }

    public String getCodigoReferencia() {
        return codigoReferencia;
    }

    public void setCodigoReferencia(String codigoReferencia) {
        this.codigoReferencia = codigoReferencia;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getRecorrencia() {
        return recorrencia;
    }

    public void setRecorrencia(String recorrencia) {
        this.recorrencia = recorrencia;
    }

    public String getQuantidadeRecorrencia() {
        return quantidadeRecorrencia;
    }

    public void setQuantidadeRecorrencia(String quantidadeRecorrencia) {
        this.quantidadeRecorrencia = quantidadeRecorrencia;
    }

    public Boolean getAgendado() {
        return agendado;
    }

    public void setAgendado(Boolean agendado) {
        this.agendado = agendado;
    }

    public String getFormaMovimentacao() {
        return formaMovimentacao;
    }

    public void setFormaMovimentacao(String formaMovimentacao) {
        this.formaMovimentacao = formaMovimentacao;
    }

    public String getContaBancaria() {
        return contaBancaria;
    }

    public void setContaBancaria(String contaBancaria) {
        this.contaBancaria = contaBancaria;
    }

    public String getNotaFiscal() {
        return notaFiscal;
    }

    public void setNotaFiscal(String notaFiscal) {
        this.notaFiscal = notaFiscal;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getCentroCusto() {
        return centroCusto;
    }

    public void setCentroCusto(String centroCusto) {
        this.centroCusto = centroCusto;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public String getDocumentoReferencia() {
        return documentoReferencia;
    }

    public void setDocumentoReferencia(String documentoReferencia) {
        this.documentoReferencia = documentoReferencia;
    }
}
