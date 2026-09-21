package com.finvista.service;

import com.finvista.dto.DashboardResponse;
import com.finvista.model.FinancialHistory;
import com.finvista.repository.FinancialHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class DashboardService {

    private final FinancialHistoryRepository financialHistoryRepository;
    private final FinancialCalculationService calculationService;

    public DashboardService(
            FinancialHistoryRepository financialHistoryRepository,
            FinancialCalculationService calculationService
    ) {
        this.financialHistoryRepository =
                financialHistoryRepository;

        this.calculationService =
                calculationService;
    }

    public DashboardResponse obterDashboard() {

        FinancialHistory historico =
                financialHistoryRepository
                        .findFirstByOrderByPeriodoDesc()
                        .orElse(null);

        BigDecimal receita =
                historico != null &&
                historico.getReceita() != null
                        ? historico.getReceita()
                        : BigDecimal.ZERO;

        BigDecimal despesa =
                historico != null &&
                historico.getDespesa() != null
                        ? historico.getDespesa()
                        : BigDecimal.ZERO;

        BigDecimal resultado =
                calculationService.calcularResultado(
                        receita,
                        despesa
                );

        BigDecimal margem =
                calculationService.calcularMargem(
                        receita,
                        resultado
                );

        return new DashboardResponse(
                receita,
                despesa,
                resultado,
                margem
        );
    }
}