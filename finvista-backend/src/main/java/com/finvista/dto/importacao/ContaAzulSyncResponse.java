package com.finvista.dto.importacao;

import java.time.LocalDate;
import java.util.List;

public final class ContaAzulSyncResponse {

    private ContaAzulSyncResponse() {
    }

    public record Plano(String arquivo, LocalDate dataCorte, String token,
            int linhas, List<Item> itens) {

    }

    public record Item(int linha, String descricao, String valorOriginal,
            String sugestao, List<Long> candidatos, List<String> avisos) {

    }

    public record Decisao(int linha, String acao, Long lancamentoId) {

    }

    public record Resultado(Long loteId, int criados, int atualizados,
            int mantidos, int pendentes) {}

    public record Candidato(Long id, String descricao, LocalDate competencia, LocalDate vencimento,
            java.math.BigDecimal original, java.math.BigDecimal realizado, java.math.BigDecimal aberto,
            String situacao, String contraparte, String referencia, String categoria, String centro) {}        
}
