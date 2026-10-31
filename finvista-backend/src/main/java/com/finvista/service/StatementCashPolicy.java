package com.finvista.service;

import java.time.LocalDate;
import java.util.Set;

public final class StatementCashPolicy {

    public static final String HISTORICAL_START = "finvista:inicio historico conferido";

    private StatementCashPolicy() {
    }

    public static String exclusion(ContaAzulStatementService.Line line, LocalDate today) {
        if (line.movimento() == null || line.valor() == null || line.erros() == null || !line.erros().isEmpty()) {
            return "Movimento com data, valor ou estrutura inválida";
        }
        if (line.movimento().isAfter(today)) {
            return "Movimentos com data futura, fora das baixas realizadas";
        }
        String start = line.campos() == null ? null : line.campos().get(HISTORICAL_START);
        if (start != null && !start.isBlank()) {
            try {
                if (line.movimento().isBefore(LocalDate.parse(start))) {
                    return "Data anterior ao início histórico conferido pelo administrador";
                }
            } catch (java.time.format.DateTimeParseException e) {
                return "Regra histórica inválida: revisar a classificação do movimento";
            }
        }
        if (!ContaAzulStatementService.normalize(line.origem()).equals("lancamento financeiro")) {
            return "Transferências e outras origens, preservadas fora dos indicadores";
        }
        if (!Set.of("receita", "despesa").contains(ContaAzulStatementService.normalize(line.tipo()))
                || !Set.of("conciliado", "quitado", "pago", "recebido").contains(ContaAzulStatementService.normalize(line.situacao()))) {
            return "Movimentações sem tipo financeiro ou situação de pagamento reconhecida";
        }
        return null;
    }
}
