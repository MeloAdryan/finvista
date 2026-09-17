package com.finvista.repository;

import com.finvista.model.CashFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashFlowRepository
        extends JpaRepository<CashFlow, Long> {

    List<CashFlow> findAllByOrderByPeriodoAsc();
}