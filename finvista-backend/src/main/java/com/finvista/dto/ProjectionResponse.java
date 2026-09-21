package com.finvista.dto;

import java.math.BigDecimal;

public record ProjectionResponse(
        String mes,
        String periodo,
        BigDecimal receitaRealizada,
        BigDecimal receitaProjetada,
        BigDecimal receita,
        BigDecimal despesa,
        BigDecimal resultado,
        BigDecimal margem,
        BigDecimal saldo
) {
}