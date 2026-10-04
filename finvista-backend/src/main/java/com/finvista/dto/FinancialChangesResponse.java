package com.finvista.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinancialChangesResponse(
        String mesAtual,
        String mesAnterior,
        BigDecimal despesaAtual,
        BigDecimal despesaAnterior,
        BigDecimal diferenca,
        BigDecimal variacaoPercentual,
        int lancamentosAtual,
        int lancamentosAnterior,
        List<CategoryChange> categorias
) {
    public record CategoryChange(
            String categoria,
            BigDecimal valorAtual,
            BigDecimal valorAnterior,
            BigDecimal diferenca
    ) {}
}