package com.finvista.dto.importacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContaAzulConferenceResponse(
        String arquivo, String tipo, LocalDate dataCorte,
        int linhasLidas, int aceitas, int rejeitadas, int linhasComAvisos,
        BigDecimal valorOriginal, BigDecimal valorTotalRealizado,
        BigDecimal valorTotalAberto, List<Linha> linhas) {

    public record Linha(int numero, LocalDate competencia, LocalDate vencimento,
            LocalDate prevista, LocalDate ultimoPagamento, String descricao,
            String situacao, BigDecimal original, BigDecimal realizado,
            BigDecimal aberto, BigDecimal jurosRealizado, BigDecimal multaRealizada,
            BigDecimal descontoRealizado, BigDecimal totalRealizado,
            BigDecimal jurosPrevisto, BigDecimal multaPrevista,
            BigDecimal descontoPrevisto, BigDecimal totalAberto,
            List<Rateio> rateios, List<String> avisos, List<String> erros) {

    }

    public record Rateio(int bloco, String categoria, BigDecimal valorCategoria,
            String centro, BigDecimal valorCentro) {

    }
}
