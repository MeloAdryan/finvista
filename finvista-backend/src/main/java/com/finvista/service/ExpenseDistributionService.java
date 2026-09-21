package com.finvista.service;

import com.finvista.dto.ExpenseDistributionResponse;
import com.finvista.model.ExpenseDistribution;
import com.finvista.repository.ExpenseDistributionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExpenseDistributionService {

    private final ExpenseDistributionRepository expenseDistributionRepository;

    public ExpenseDistributionService(
            ExpenseDistributionRepository expenseDistributionRepository
    ) {
        this.expenseDistributionRepository =
                expenseDistributionRepository;
    }

    public List<ExpenseDistributionResponse> listar() {

        return expenseDistributionRepository
                .findAllByOrderByValorDesc()
                .stream()
                .map(this::criarResposta)
                .toList();
    }

    private ExpenseDistributionResponse criarResposta(
            ExpenseDistribution expenseDistribution
    ) {
        return new ExpenseDistributionResponse(
                expenseDistribution.getCategoria(),
                expenseDistribution.getValor()
        );
    }
}