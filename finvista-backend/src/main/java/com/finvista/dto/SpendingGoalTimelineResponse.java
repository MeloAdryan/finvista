package com.finvista.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public record SpendingGoalTimelineResponse(Long id, LocalDate inicio, LocalDate fim, LocalDate hoje,
        String situacaoTemporal, int diasTotais, int diasDecorridos, BigDecimal limite,
        BigDecimal totalRegistrado, BigDecimal registradoAteHoje, BigDecimal registradoDepoisHoje,
        BigDecimal ritmoIdealPercentual, BigDecimal estimativaFim, List<Ponto> pontos, List<Categoria> categorias) {

    public record Ponto(LocalDate data, BigDecimal acumuladoRegistrado, BigDecimal ritmoIdeal, BigDecimal estimativa) {}

    public record Categoria(String categoria, BigDecimal valor) {}
}
