package com.finvista.dto.importacao;

import java.util.List;
import java.util.Map;

public class ImportPreviewResponse {

    private String arquivo;
    private String tipoArquivo;
    private List<String> colunas;
    private List<Map<String, String>> linhas;
    private int quantidadeLinhas;

    public ImportPreviewResponse() {
    }

    public ImportPreviewResponse(
            String arquivo,
            String tipoArquivo,
            List<String> colunas,
            List<Map<String, String>> linhas,
            int quantidadeLinhas
    ) {
        this.arquivo = arquivo;
        this.tipoArquivo = tipoArquivo;
        this.colunas = colunas;
        this.linhas = linhas;
        this.quantidadeLinhas = quantidadeLinhas;
    }

    public String getArquivo() {
        return arquivo;
    }

    public void setArquivo(String arquivo) {
        this.arquivo = arquivo;
    }

    public String getTipoArquivo() {
        return tipoArquivo;
    }

    public void setTipoArquivo(String tipoArquivo) {
        this.tipoArquivo = tipoArquivo;
    }

    public List<String> getColunas() {
        return colunas;
    }

    public void setColunas(List<String> colunas) {
        this.colunas = colunas;
    }

    public List<Map<String, String>> getLinhas() {
        return linhas;
    }

    public void setLinhas(
            List<Map<String, String>> linhas
    ) {
        this.linhas = linhas;
    }

    public int getQuantidadeLinhas() {
        return quantidadeLinhas;
    }

    public void setQuantidadeLinhas(
            int quantidadeLinhas
    ) {
        this.quantidadeLinhas = quantidadeLinhas;
    }
}