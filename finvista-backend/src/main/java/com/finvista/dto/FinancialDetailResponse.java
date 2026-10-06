package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialDetailResponse(
        Long clienteId, String tipo, String mes,
        LocalDate dataInicial, LocalDate dataFinal,
        BigDecimal total, int quantidadeLancamentos,
        int pagina, int tamanho, int totalPaginas,
        String explicacao, List<Item> itens
        ) {

    public record Item(
            Long id, LocalDate dataAnalise, LocalDate vencimento,
            LocalDate realizacao, String descricao, String situacao,
            BigDecimal valorContabilizado, BigDecimal valorOriginal,
            BigDecimal principalRealizado, BigDecimal principalAberto,
            String origem
            ) {

    }
}
