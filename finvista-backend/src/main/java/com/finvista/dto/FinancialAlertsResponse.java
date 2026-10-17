package com.finvista.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialAlertsResponse(
        Long clienteId, LocalDate inicio, LocalDate fim, String categoria,
        int total, List<Alerta> alertas, Comparacao comparacao) {

    public enum Gravidade {
        INFORMATIVO, ATENCAO, CRITICO
    }

    public enum Tipo {
        META_LIMITE, ORCAMENTO_EXCEDENTE, AUMENTO_DESPESAS
    }

    public enum Comparacao {
        DISPONIVEL, SEM_REGISTROS, BASE_NAO_POSITIVA, PERIODO_EM_ANDAMENTO
    }

    public record Alerta(
            String id, Tipo tipo, Gravidade gravidade, Long entidadeId,
            String titulo, String motivo, LocalDate inicio, LocalDate fim,
            BigDecimal valorAtual, BigDecimal valorBase, BigDecimal diferenca,
            BigDecimal percentual, BigDecimal limiarPercentual,
            LocalDate inicioAnterior, LocalDate fimAnterior,
            String categoria, String centroCusto, boolean encerrado) {}
}
