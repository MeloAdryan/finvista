package com.finvista.dto;

import java.time.LocalDate;
import java.util.List;

public record RealizedCashFlowResponse(Long clienteId, LocalDate inicio, LocalDate fim,
        int lancamentosConferidos, int lancamentosSemDetalhamento, int baixasNoPeriodo,
        boolean parcial, String base, List<CashFlowResponse> meses) {}
