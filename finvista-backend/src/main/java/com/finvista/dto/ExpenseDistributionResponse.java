package com.finvista.dto;

import java.math.BigDecimal;

public record ExpenseDistributionResponse(
        String categoria,
        BigDecimal valor
) {
}