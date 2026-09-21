package com.finvista.repository;

import com.finvista.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BudgetRepository
        extends JpaRepository<Budget, Long> {

    List<Budget>
    findAllByOrderByProbabilidadeDesc();

    List<Budget>
    findByDataPrevisaoFechamentoBetweenOrderByDataPrevisaoFechamentoAsc(
            LocalDate dataInicial,
            LocalDate dataFinal
    );
}