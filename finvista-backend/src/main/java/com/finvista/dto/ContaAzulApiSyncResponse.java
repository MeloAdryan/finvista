package com.finvista.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ContaAzulApiSyncResponse {

    private ContaAzulApiSyncResponse() {
    }

    public record Pedido(Long clienteId) {

        @JsonAnySetter
        public void rejeitar(String nome, Object valor) {
            throw new IllegalArgumentException("Campo não permitido: " + nome);
        }
    }

    public record Confirmacao(Long clienteId, String token, boolean confirmado, boolean confirmoNaoDuplicado) {

        @JsonAnySetter
        public void rejeitar(String nome, Object valor) {
            throw new IllegalArgumentException("Campo não permitido: " + nome);
        }
    }

    public record PedidoVinculo(Long clienteId, Long lancamentoId) {

        @JsonAnySetter
        public void rejeitar(String nome, Object valor) {
            throw new IllegalArgumentException("Campo não permitido: " + nome);
        }
    }

    public record Rateio(String categoria, BigDecimal valor, String centro, BigDecimal valorCentro) {}

    public record Financeiro(BigDecimal principalPago, BigDecimal principalAberto, BigDecimal jurosPago,
            BigDecimal multaPaga, BigDecimal descontoPago, BigDecimal jurosAberto, BigDecimal multaAberta,
            BigDecimal descontoAberto, LocalDate ultimaBaixa, BigDecimal taxaPaga, BigDecimal taxaAberta,
            BigDecimal perda, BigDecimal renegociado) {
        public Financeiro(BigDecimal principalPago, BigDecimal principalAberto, BigDecimal jurosPago,
                BigDecimal multaPaga, BigDecimal descontoPago, BigDecimal jurosAberto, BigDecimal multaAberta,
                BigDecimal descontoAberto, LocalDate ultimaBaixa) {
            this(principalPago, principalAberto, jurosPago, multaPaga, descontoPago, jurosAberto, multaAberta,
                    descontoAberto, ultimaBaixa, null, null, null, null);
        }
    }

    public record Resumo(String descricao, String tipo, LocalDate competencia, LocalDate vencimento,
            BigDecimal valor, BigDecimal pago, BigDecimal pendente, List<Rateio> rateios, Financeiro financeiro) {
        public Resumo(String descricao, String tipo, LocalDate competencia, LocalDate vencimento,
                BigDecimal valor, BigDecimal pago, BigDecimal pendente, List<Rateio> rateios) {
            this(descricao, tipo, competencia, vencimento, valor, pago, pendente, rateios, null);
        }
    }

    public record Previa(Long clienteId, String clienteNome, String parcelaId, String acao, Long lancamentoId,
            Instant expiraEm, String token, Resumo antes, Resumo depois, List<String> avisos) {}

    public record Resultado(UUID operacaoId, Long clienteId, String parcelaId, String acao,
            Long lancamentoId, Instant concluidoEm) {}

    public record Auditoria(Resultado resultado, Long usuarioId, JsonNode antes, JsonNode depois) {}
}
