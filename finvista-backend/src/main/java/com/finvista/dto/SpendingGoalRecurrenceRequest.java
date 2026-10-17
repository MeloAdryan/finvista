package com.finvista.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingGoalRecurrenceRequest(String nome, String recorrencia,
        LocalDate dataInicio, LocalDate dataFim, BigDecimal valorLimite,
        Integer percentualAlerta, String categoria, String centroCusto) {

    @JsonAnySetter
    public void rejeitarCampo(String campo, Object valor) {
        throw new IllegalArgumentException("Campo não permitido no cadastro: " + campo);
    }
}

