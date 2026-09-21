package com.finvista.dto;

import java.math.BigDecimal;

public record HistoryResponse(
        String periodo,
        BigDecimal receita,
        BigDecimal despesa,
        BigDecimal resultado,
        BigDecimal margem
) {
}