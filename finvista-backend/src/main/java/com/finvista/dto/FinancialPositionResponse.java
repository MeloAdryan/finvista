package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialPositionResponse(Long clienteId, LocalDate referencia,
        Grupo receber, Grupo pagar, int semValorAberto, int semVencimento, List<Cobertura> cobertura, LocalDate vencimentoInicio, LocalDate vencimentoFim) {

    public FinancialPositionResponse(Long clienteId, LocalDate referencia, Grupo receber, Grupo pagar, int semValorAberto, int semVencimento, List<Cobertura> cobertura) {
        this(clienteId, referencia, receber, pagar, semValorAberto, semVencimento, cobertura, null, null);
    }

    public FinancialPositionResponse(Long clienteId, LocalDate referencia, Grupo receber, Grupo pagar, int semValorAberto, int semVencimento) {
        this(clienteId, referencia, receber, pagar, semValorAberto, semVencimento, List.of());
    }

    public record Cobertura(String origem, int semValorAberto, int semVencimento, LocalDate primeiraDataAnalise, LocalDate ultimaDataAnalise) {}

    public record Grupo(BigDecimal vencidos, BigDecimal aVencer, BigDecimal venceHoje, BigDecimal aVencerAposHoje) {

        public Grupo(BigDecimal vencidos, BigDecimal aVencer) {
            this(vencidos, aVencer, null, null);
        }
    }
}
