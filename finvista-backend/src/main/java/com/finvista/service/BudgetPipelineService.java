package com.finvista.service;

import com.finvista.dto.BudgetPipelineResponse;
import com.finvista.dto.BudgetRequest;
import com.finvista.model.Budget;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.BudgetRepository;
import com.finvista.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BudgetPipelineService {

    private final BudgetRepository budgetRepository;
    private final FinancialTransactionRepository
            financialTransactionRepository;

    public BudgetPipelineService(
            BudgetRepository budgetRepository,
            FinancialTransactionRepository financialTransactionRepository
    ) {
        this.budgetRepository =
                budgetRepository;

        this.financialTransactionRepository =
                financialTransactionRepository;
    }

    public List<BudgetPipelineResponse> listar() {
        return budgetRepository
                .findAllByOrderByDataInicioDesc()
                .stream()
                .map(this::criarResposta)
                .toList();
    }

    public BudgetPipelineResponse cadastrar(
            BudgetRequest request
    ) {
        validar(request);

        Budget budget =
                new Budget(
                        request.nome().trim(),
                        normalizarOpcional(
                                request.centroCusto()
                        ),
                        normalizarOpcional(
                                request.categoria()
                        ),
                        request.valorPlanejado(),
                        request.dataInicio(),
                        request.dataFim()
                );

        Budget salvo =
                budgetRepository.save(budget);

        return criarResposta(salvo);
    }

    private BudgetPipelineResponse criarResposta(
            Budget budget
    ) {
        List<FinancialTransaction> despesas =
                financialTransactionRepository
                        .findByTipoAndDataBetweenOrderByDataAsc(
                                "DESPESA",
                                budget.getDataInicio(),
                                budget.getDataFim()
                        );

        BigDecimal valorUtilizado =
                despesas.stream()
                        .filter(
                                despesa ->
                                        correspondeAoOrcamento(
                                                budget,
                                                despesa
                                        )
                        )
                        .map(
                                FinancialTransaction::getValor
                        )
                        .filter(
                                valor -> valor != null
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal valorDisponivel =
                budget.getValorPlanejado()
                        .subtract(valorUtilizado);

        BigDecimal percentualUtilizado =
                calcularPercentualUtilizado(
                        budget.getValorPlanejado(),
                        valorUtilizado
                );

        return new BudgetPipelineResponse(
                budget.getId(),
                budget.getNome(),
                budget.getCentroCusto(),
                budget.getCategoria(),
                budget.getValorPlanejado(),
                valorUtilizado,
                valorDisponivel,
                percentualUtilizado,
                budget.getDataInicio(),
                budget.getDataFim()
        );
    }

    private boolean correspondeAoOrcamento(
            Budget budget,
            FinancialTransaction despesa
    ) {
        boolean centroCustoCorresponde =
                budget.getCentroCusto() == null
                        || iguaisIgnorandoMaiusculas(
                                budget.getCentroCusto(),
                                despesa.getCentroCusto()
                        );

        boolean categoriaCorresponde =
                budget.getCategoria() == null
                        || iguaisIgnorandoMaiusculas(
                                budget.getCategoria(),
                                despesa.getCategoria()
                        );

        return centroCustoCorresponde
                && categoriaCorresponde;
    }

    private boolean iguaisIgnorandoMaiusculas(
            String primeiro,
            String segundo
    ) {
        if (primeiro == null || segundo == null) {
            return false;
        }

        return primeiro
                .trim()
                .equalsIgnoreCase(
                        segundo.trim()
                );
    }

    private BigDecimal calcularPercentualUtilizado(
            BigDecimal valorPlanejado,
            BigDecimal valorUtilizado
    ) {
        if (valorPlanejado == null
                || valorPlanejado.compareTo(
                        BigDecimal.ZERO
                ) == 0) {
            return BigDecimal.ZERO;
        }

        return valorUtilizado
                .multiply(
                        new BigDecimal("100")
                )
                .divide(
                        valorPlanejado,
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private String normalizarOpcional(
            String valor
    ) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim();
    }

    private void validar(
            BudgetRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Os dados do orçamento são obrigatórios."
            );
        }

        if (request.nome() == null
                || request.nome().isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do orçamento é obrigatório."
            );
        }

        if (request.valorPlanejado() == null
                || request.valorPlanejado()
                        .compareTo(
                                BigDecimal.ZERO
                        ) <= 0) {
            throw new IllegalArgumentException(
                    "O valor planejado deve ser maior que zero."
            );
        }

        if (request.dataInicio() == null) {
            throw new IllegalArgumentException(
                    "A data inicial é obrigatória."
            );
        }

        if (request.dataFim() == null) {
            throw new IllegalArgumentException(
                    "A data final é obrigatória."
            );
        }

        if (request.dataFim()
                .isBefore(
                        request.dataInicio()
                )) {
            throw new IllegalArgumentException(
                    "A data final não pode ser anterior à data inicial."
            );
        }
    }
}