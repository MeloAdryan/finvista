package com.finvista.repository;

import com.finvista.model.SpendingGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpendingGoalRepository
        extends JpaRepository<SpendingGoal, Long> {

    List<SpendingGoal>
    findByClienteIdOrderByDataInicioDesc(
            Long clienteId
    );

    Optional<SpendingGoal>
    findByIdAndClienteId(
            Long id,
            Long clienteId
    );
}