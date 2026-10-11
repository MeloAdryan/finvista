package com.finvista.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.finvista.model.SpendingGoal;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A criação recebe somente campos editáveis; ID e proprietário são do servidor.
 */
public record SpendingGoalCreateRequest(String tipo, LocalDate dataInicio, LocalDate dataFim,
        BigDecimal valorLimite, Integer percentualAlerta, String categoria, String centroCusto) {

    @JsonAnySetter
    public void rejeitarCampo(String campo, Object valor) {
        throw new IllegalArgumentException("Campo não permitido na criação de meta: " + campo);
    }

    public SpendingGoal novaMeta() {
        SpendingGoal meta = new SpendingGoal(tipo, dataInicio, dataFim, valorLimite, percentualAlerta);
        meta.setCategoria(categoria);
        meta.setCentroCusto(centroCusto);
        return meta;
    }
}
