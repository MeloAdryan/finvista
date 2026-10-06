package com.finvista.repository;

import com.finvista.model.CostCategoryClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.Optional;

public interface CostCategoryClassificationRepository extends JpaRepository<CostCategoryClassification, Long> {

    List<CostCategoryClassification> findByClienteId(Long clienteId);

    Optional<CostCategoryClassification> findByClienteIdAndCategoriaChave(Long clienteId, String categoriaChave);
}
