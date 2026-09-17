package com.finvista.repository;

import com.finvista.model.FinancialHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialHistoryRepository
        extends JpaRepository<FinancialHistory, Long> {

    List<FinancialHistory> findAllByOrderByPeriodoAsc();

    Optional<FinancialHistory> findFirstByOrderByPeriodoDesc();
}