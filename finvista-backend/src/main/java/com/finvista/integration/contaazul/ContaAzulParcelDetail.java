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
        @JsonProperty("perda")
        Ajuste perda,
        @JsonProperty("renegociacao")
        Ajuste renegociacao,
        List<Baixa> baixas,
        @JsonProperty("id_conta_financeira") String contaId,
        @JsonProperty("conta_financeira") com.fasterxml.jackson.databind.JsonNode conta) {

    public ContaAzulParcelDetail { conta = contaSegura(conta); }

    private static com.fasterxml.jackson.databind.JsonNode contaSegura(com.fasterxml.jackson.databind.JsonNode source) {
        if (source == null || source.isNull()) return null;
        if (source.isTextual()) return source.asText().isBlank() || source.asText().length() > 160 ? null : source.deepCopy();
        if (!source.isObject()) return null;
        var result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        for (String key : List.of("id", "nome")) {
            var value = source.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 255)
                result.put(key, value.asText());
        }
        return result;
    }

    public ContaAzulParcelDetail(String id, String descricao, String status, LocalDate vencimento,
            BigDecimal liquido, BigDecimal pago, BigDecimal pendente, Composicao composicao, Evento evento,
            Ajuste perda, Ajuste renegociacao, List<Baixa> baixas) {
        this(id, descricao, status, vencimento, liquido, pago, pendente, composicao, evento, perda, renegociacao, baixas, null, null);
    }

    public ContaAzulParcelDetail(String id, String descricao, String status, LocalDate vencimento,
            BigDecimal liquido, BigDecimal pago, BigDecimal pendente, Composicao composicao, Evento evento,
            Ajuste perda, Ajuste renegociacao) {
        this(id, descricao, status, vencimento, liquido, pago, pendente, composicao, evento, perda, renegociacao, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Baixa(String id, @JsonProperty("id_parcela") String parcelaId,
            @JsonProperty("data_pagamento") LocalDate data,
            @JsonProperty("valor_composicao") Composicao composicao,
            @JsonProperty("conta_financeira") com.fasterxml.jackson.databind.JsonNode conta,
            @JsonProperty("metodo_pagamento") String metodo,
            String origem, Long versao) {
        public Baixa { conta = contaSegura(conta); }
        public Baixa(String id, String parcelaId, LocalDate data, Composicao composicao) {
            this(id, parcelaId, data, composicao, null, null, null, null);
        }
    }

    public ContaAzulParcelDetail(String id, String descricao, String status, LocalDate vencimento,
            BigDecimal liquido, BigDecimal pago, BigDecimal pendente, Composicao composicao, Evento evento) {
        this(id, descricao, status, vencimento, liquido, pago, pendente, composicao, evento, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ajuste(BigDecimal valor, LocalDate data, String id) {
        public Ajuste(BigDecimal valor) { this(valor, null, null); }
    }

    @JsonProperty("valor_liquido_conferido")
    public BigDecimal liquidoConferido() {
        if (liquido != null) {
            return liquido;
        }
        if (evento == null || evento.condicao() == null || evento.condicao().quantidade() == null
                || evento.condicao().quantidade() < 1 || composicao == null || composicao.liquido() == null
                || !("RECEITA".equals(evento.tipo()) || "DESPESA".equals(evento.tipo()))) return null;

        if ((perda != null && !"PERDIDO".equals(status)) || (renegociacao != null && !"RENEGOCIADO".equals(status))) return null;
        // Mantém a conferência de leitura legada. A gravação exige baixas explícitas na policy.
        boolean aberto = "PENDENTE".equals(status) || "EM_ABERTO".equals(status) || "ATRASADO".equals(status);
        boolean quitado = "QUITADO".equals(status) || "RECEBIDO".equals(status);
        if (baixas == null && dinheiro(composicao.bruto()) && dinheiro(composicao.liquido())
                && dinheiro(pago) && dinheiro(pendente)
                && zero(composicao.juros()) && zero(composicao.multa())
                && zero(composicao.desconto()) && zero(composicao.taxa())
                && composicao.bruto().compareTo(composicao.liquido()) == 0
                && pago.add(pendente).compareTo(composicao.liquido()) == 0
                && ((aberto && pago.signum() == 0) || (quitado && pendente.signum() == 0)))
            return composicao.liquido();
        try { return ContaAzulSettlementPolicy.conferir(this).liquido(); }
        catch (IllegalArgumentException e) { return null; }
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
