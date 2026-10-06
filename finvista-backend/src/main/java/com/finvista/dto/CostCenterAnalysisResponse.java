package com.finvista.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CostCenterAnalysisResponse(
        LocalDate inicio, LocalDate fim, BigDecimal total,
        List<CostCenterResponse> centros
) {}