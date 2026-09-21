package com.finvista.config;

import com.finvista.model.Budget;
import com.finvista.model.CashFlow;
import com.finvista.model.CostCenter;
import com.finvista.model.ExpenseDistribution;
import com.finvista.model.FinancialHistory;

import com.finvista.repository.BudgetRepository;
import com.finvista.repository.CashFlowRepository;
import com.finvista.repository.CostCenterRepository;
import com.finvista.repository.ExpenseDistributionRepository;
import com.finvista.repository.FinancialHistoryRepository;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    @ConditionalOnProperty(
            name = "finvista.seed.enabled",
            havingValue = "true"
    )
    CommandLineRunner carregarDadosIniciais(
            FinancialHistoryRepository historyRepository,
            CashFlowRepository cashFlowRepository,
            CostCenterRepository costCenterRepository,
            ExpenseDistributionRepository expenseDistributionRepository,
            BudgetRepository budgetRepository
    ) {
        return args -> {

            carregarHistoricoFinanceiro(historyRepository);

            carregarFluxoCaixa(cashFlowRepository);

            carregarCentrosCusto(costCenterRepository);

            carregarDistribuicaoDespesas(
                    expenseDistributionRepository
            );

            carregarOrcamentos(budgetRepository);
        };
    }

    private void carregarHistoricoFinanceiro(
            FinancialHistoryRepository repository
    ) {
        if (repository.count() > 0) {
            return;
        }

        repository.save(
                new FinancialHistory(
                        "2026-04",
                        new BigDecimal("125000.00"),
                        new BigDecimal("83000.00")
                )
        );

        repository.save(
                new FinancialHistory(
                        "2026-05",
                        new BigDecimal("132000.00"),
                        new BigDecimal("87000.00")
                )
        );

        repository.save(
                new FinancialHistory(
                        "2026-06",
                        new BigDecimal("138000.00"),
                        new BigDecimal("89000.00")
                )
        );

        repository.save(
                new FinancialHistory(
                        "2026-07",
                        new BigDecimal("142000.00"),
                        new BigDecimal("90000.00")
                )
        );

        repository.save(
                new FinancialHistory(
                        "2026-08",
                        new BigDecimal("147000.00"),
                        new BigDecimal("91000.00")
                )
        );

        repository.save(
                new FinancialHistory(
                        "2026-09",
                        new BigDecimal("150000.00"),
                        new BigDecimal("92000.00")
                )
        );
    }

    private void carregarFluxoCaixa(
            CashFlowRepository repository
    ) {
        if (repository.count() > 0) {
            return;
        }

        repository.save(
                new CashFlow(
                        "2026-09",
                        new BigDecimal("150000.00"),
                        new BigDecimal("92000.00")
                )
        );

        repository.save(
                new CashFlow(
                        "2026-10",
                        new BigDecimal("160000.00"),
                        new BigDecimal("97000.00")
                )
        );

        repository.save(
                new CashFlow(
                        "2026-11",
                        new BigDecimal("175000.00"),
                        new BigDecimal("102000.00")
                )
        );

        repository.save(
                new CashFlow(
                        "2026-12",
                        new BigDecimal("168000.00"),
                        new BigDecimal("110000.00")
                )
        );

        repository.save(
                new CashFlow(
                        "2027-01",
                        new BigDecimal("182000.00"),
                        new BigDecimal("108000.00")
                )
        );

        repository.save(
                new CashFlow(
                        "2027-02",
                        new BigDecimal("195000.00"),
                        new BigDecimal("115000.00")
                )
        );
    }

    private void carregarCentrosCusto(
            CostCenterRepository repository
    ) {
        if (repository.count() > 0) {
            return;
        }

        repository.save(
                new CostCenter(
                        "Produção",
                        new BigDecimal("38000.00")
                )
        );

        repository.save(
                new CostCenter(
                        "Logística",
                        new BigDecimal("21000.00")
                )
        );

        repository.save(
                new CostCenter(
                        "Administrativo",
                        new BigDecimal("14000.00")
                )
        );

        repository.save(
                new CostCenter(
                        "Comercial",
                        new BigDecimal("11000.00")
                )
        );

        repository.save(
                new CostCenter(
                        "Manutenção",
                        new BigDecimal("8000.00")
                )
        );
    }

    private void carregarDistribuicaoDespesas(
            ExpenseDistributionRepository repository
    ) {
        if (repository.count() > 0) {
            return;
        }

        repository.save(
                new ExpenseDistribution(
                        "Folha",
                        new BigDecimal("32000.00")
                )
        );

        repository.save(
                new ExpenseDistribution(
                        "Fornecedores",
                        new BigDecimal("22000.00")
                )
        );

        repository.save(
                new ExpenseDistribution(
                        "Combustível",
                        new BigDecimal("14000.00")
                )
        );

        repository.save(
                new ExpenseDistribution(
                        "Impostos",
                        new BigDecimal("12000.00")
                )
        );

        repository.save(
                new ExpenseDistribution(
                        "Manutenção",
                        new BigDecimal("8000.00")
                )
        );

        repository.save(
                new ExpenseDistribution(
                        "Outros",
                        new BigDecimal("4000.00")
                )
        );
    }

    private void carregarOrcamentos(
        BudgetRepository repository
) {
    if (repository.count() > 0) {
        return;
    }

    repository.save(
            new Budget(
                    "Madeireira Norte",
                    new BigDecimal("120000.00"),
                    "Proposta enviada",
                    70,
                    LocalDate.of(2026, 11, 15)
            )
    );

    repository.save(
            new Budget(
                    "Construtora Acre",
                    new BigDecimal("85000.00"),
                    "Negociação",
                    80,
                    LocalDate.of(2026, 12, 10)
            )
    );

    repository.save(
            new Budget(
                    "Grupo Florestal",
                    new BigDecimal("150000.00"),
                    "Em análise",
                    50,
                    LocalDate.of(2026, 12, 20)
            )
    );

    repository.save(
            new Budget(
                    "Indústria Amazônia",
                    new BigDecimal("65000.00"),
                    "Contato inicial",
                    30,
                    LocalDate.of(2027, 1, 15)
            )
    );

    repository.save(
            new Budget(
                    "Madeiras Brasil",
                    new BigDecimal("95000.00"),
                    "Negociação",
                    90,
                    LocalDate.of(2027, 2, 10)
            )
    );
}
}