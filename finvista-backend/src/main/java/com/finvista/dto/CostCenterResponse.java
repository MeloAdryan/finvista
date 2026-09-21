package com.finvista.dto;

import java.math.BigDecimal;

public record CostCenterResponse(
        String nome,
        BigDecimal valor
) {
}