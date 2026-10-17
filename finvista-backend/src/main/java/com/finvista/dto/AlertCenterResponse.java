package com.finvista.dto;

import com.finvista.dto.FinancialAlertsResponse.Alerta;
import com.finvista.dto.FinancialAlertsResponse.Comparacao;
import com.finvista.model.FinancialAlertRecord.Estado;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AlertCenterResponse(Long clienteId, LocalDate inicio, LocalDate fim, int pagina,
        int tamanho, long total, int totalPaginas, List<Registro> registros) {
    public record Registro(Long id, Alerta alerta, Estado estado, boolean lido, int ocorrencias,
            Instant criadoEm, Instant alteradoEm, Instant verificadoEm, Instant resolvidoEm) {}
    public record Atualizacao(Long clienteId, LocalDate inicio, LocalDate fim,
            Instant atualizadoEm, int avisosEncontrados, Comparacao comparacao) {}
    public record Periodo(LocalDate inicio, LocalDate fim) {}
}
