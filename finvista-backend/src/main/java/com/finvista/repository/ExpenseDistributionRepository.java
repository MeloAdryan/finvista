package com.finvista.repository;

import com.finvista.model.ExpenseDistribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseDistributionRepository
        extends JpaRepository<ExpenseDistribution, Long> {

    List<ExpenseDistribution> findAllByOrderByValorDesc();
}