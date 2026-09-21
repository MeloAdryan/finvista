package com.finvista.repository;

import com.finvista.model.SpendingGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpendingGoalRepository
        extends JpaRepository<SpendingGoal, Long> {

    List<SpendingGoal>
    findAllByOrderByDataInicioDesc();
}