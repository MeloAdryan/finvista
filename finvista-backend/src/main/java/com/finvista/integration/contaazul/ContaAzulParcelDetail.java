package com.finvista.integration.contaazul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        Evento evento,
        @JsonProperty(value = "perda", access = JsonProperty.Access.WRITE_ONLY)
        Ajuste perda,
        @JsonProperty(value = "renegociacao", access = JsonProperty.Access.WRITE_ONLY)
        Ajuste renegociacao) {

    public ContaAzulParcelDetail(String id, String descricao, String status, LocalDate vencimento,
            BigDecimal liquido, BigDecimal pago, BigDecimal pendente, Composicao composicao, Evento evento) {
        this(id, descricao, status, vencimento, liquido, pago, pendente, composicao, evento, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ajuste(BigDecimal valor) {}

    @JsonProperty("valor_liquido_conferido")
    public BigDecimal liquidoConferido() {
        if (liquido != null) {
            return liquido;
        }
        if (perda != null || renegociacao != null || composicao == null || evento == null || evento.condicao() == null
                || !Integer.valueOf(1).equals(evento.condicao().quantidade())
                || !("RECEITA".equals(evento.tipo()) || "DESPESA".equals(evento.tipo()))
                || !dinheiro(composicao.bruto()) || !dinheiro(composicao.liquido())
                || !dinheiro(pago) || !dinheiro(pendente)
                || !zero(composicao.juros()) || !zero(composicao.multa())
                || !zero(composicao.desconto()) || !zero(composicao.taxa())
                || composicao.bruto().compareTo(composicao.liquido()) != 0
                || pago.add(pendente).compareTo(composicao.liquido()) != 0) {
            return null;
        }
        boolean emAberto = "PENDENTE".equals(status) || "EM_ABERTO".equals(status) || "ATRASADO".equals(status);
        boolean quitado = "QUITADO".equals(status) || "RECEBIDO".equals(status);
        if (!(emAberto && pago.signum() == 0) && !(quitado && pendente.signum() == 0)) {
            return null;
        }
        return composicao.liquido();
    }

    @JsonProperty("origem_valor_liquido")
    public String origemValorLiquido() {
        return liquido != null ? "VALOR_TOTAL_LIQUIDO"
                : liquidoConferido() != null ? "COMPOSICAO_CONFERIDA" : "NAO_CONFERIDO";
    }

    private static boolean dinheiro(BigDecimal valor) {
        return valor != null && valor.signum() >= 0 && valor.stripTrailingZeros().scale() <= 2
                && valor.compareTo(new BigDecimal("9999999999999.99")) <= 0;
    }

    private static boolean zero(BigDecimal valor) {
        return valor != null && valor.signum() == 0;
    }

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
