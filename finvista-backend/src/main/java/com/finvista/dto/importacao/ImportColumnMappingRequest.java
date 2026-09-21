package com.finvista.dto.importacao;

public class ImportColumnMappingRequest {

    private String data;
    private String descricao;
    private String tipo;
    private String valor;
    private String categoria;
    private String centroCusto;
    private String documentoReferencia;

    public ImportColumnMappingRequest() {
    }

    public ImportColumnMappingRequest(
            String data,
            String descricao,
            String tipo,
            String valor,
            String categoria,
            String centroCusto,
            String documentoReferencia
    ) {
        this.data = data;
        this.descricao = descricao;
        this.tipo = tipo;
        this.valor = valor;
        this.categoria = categoria;
        this.centroCusto = centroCusto;
        this.documentoReferencia = documentoReferencia;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
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

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
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

    public String getDocumentoReferencia() {
        return documentoReferencia;
    }

    public void setDocumentoReferencia(
            String documentoReferencia
    ) {
        this.documentoReferencia = documentoReferencia;
    }
}