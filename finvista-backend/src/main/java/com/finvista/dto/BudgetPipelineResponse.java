package com.finvista.dto;

import java.math.BigDecimal;

public record BudgetPipelineResponse(
        Long id,
        String cliente,
        BigDecimal valor,
        String status,
        Integer probabilidade,
        BigDecimal valorPonderado
) {
}