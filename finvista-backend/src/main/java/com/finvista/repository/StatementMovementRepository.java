package com.finvista.repository;

import com.finvista.model.StatementMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementMovementRepository extends JpaRepository<StatementMovement, Long> {

    List<StatementMovement> findByClienteIdOrderByIdAsc(Long clienteId);
}
