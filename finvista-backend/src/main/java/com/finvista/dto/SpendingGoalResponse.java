package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingGoalResponse(
        Long id,
        String tipo,
        LocalDate dataInicio,
        LocalDate dataFim,
        BigDecimal valorLimite,
        BigDecimal gastoAtual,
        BigDecimal percentualUtilizado,
        BigDecimal saldoMeta,
        Integer percentualAlerta,
        String status,
        String categoria,
        String centroCusto,
        BigDecimal gastoFiltrado,
        BigDecimal percentualFiltrado,
        String filtroCategoria,
        String filtroCentroCusto,
        String situacaoTemporal,
        LocalDate dataReferencia
        ) {

    public SpendingGoalResponse(Long id, String tipo, LocalDate dataInicio, LocalDate dataFim, BigDecimal valorLimite, BigDecimal gastoAtual, BigDecimal percentualUtilizado, BigDecimal saldoMeta, Integer percentualAlerta, String status, String categoria, String centroCusto, BigDecimal gastoFiltrado, BigDecimal percentualFiltrado, String filtroCategoria, String filtroCentroCusto) {
        this(id, tipo, dataInicio, dataFim, valorLimite, gastoAtual, percentualUtilizado, saldoMeta, percentualAlerta, status, categoria, centroCusto, gastoFiltrado, percentualFiltrado, filtroCategoria, filtroCentroCusto, LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")));
    }

    private SpendingGoalResponse(Long id, String tipo, LocalDate dataInicio, LocalDate dataFim, BigDecimal valorLimite, BigDecimal gastoAtual, BigDecimal percentualUtilizado, BigDecimal saldoMeta, Integer percentualAlerta, String status, String categoria, String centroCusto, BigDecimal gastoFiltrado, BigDecimal percentualFiltrado, String filtroCategoria, String filtroCentroCusto, LocalDate hoje) {
        this(id, tipo, dataInicio, dataFim, valorLimite, gastoAtual, percentualUtilizado, saldoMeta, percentualAlerta, status, categoria, centroCusto, gastoFiltrado, percentualFiltrado, filtroCategoria, filtroCentroCusto, hoje.isBefore(dataInicio) ? "FUTURA" : hoje.isAfter(dataFim) ? "ENCERRADA" : "EM_ANDAMENTO", hoje);
    }
}
