package com.finvista.integration.contaazul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO: descreve os campos da resposta, sem acesso ao banco e sem regras de
 * importação.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContaAzulParcelDetail(
        String id,
        String descricao,
        String status,
        @JsonProperty("data_vencimento")
        LocalDate vencimento,
        @JsonProperty("valor_total_liquido")
        BigDecimal liquido,
        @JsonProperty("valor_pago")
        BigDecimal pago,
        @JsonProperty("nao_pago")
        BigDecimal pendente,
        @JsonProperty("valor_composicao")
        Composicao composicao,
        Evento evento) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Composicao(
            @JsonProperty("valor_bruto")
            BigDecimal bruto,
            @JsonProperty("valor_liquido")
            BigDecimal liquido,
            BigDecimal juros, BigDecimal multa, BigDecimal desconto, BigDecimal taxa) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evento(
            String id, String tipo,
            @JsonProperty("data_competencia")
            LocalDate competencia,
            @JsonProperty("condicao_pagamento")
            Condicao condicao,
            List<Rateio> rateio) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condicao(@JsonProperty("quantidade_parcelas")
            Integer quantidade) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rateio(
            @JsonProperty("id_categoria")
            String categoriaId,
            @JsonProperty("nome_categoria")
            String categoriaNome,
            BigDecimal valor,
            @JsonProperty("valor_bruto")
            BigDecimal bruto,
            @JsonProperty("rateio_centro_custo")
            List<Centro> centros) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Centro(
            @JsonProperty("id_centro_custo")
            String centroId,
            @JsonProperty("nome_centro_custo")
            String centroNome,
            BigDecimal valor,
            @JsonProperty("valor_bruto")
            BigDecimal bruto) {}
}