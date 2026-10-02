package com.finvista.dto;

import java.time.LocalDate;

public record FinancialReferenceResponse(
        int ano,
        int mes,
        String mesAno,
        LocalDate dataReferencia
) {
}