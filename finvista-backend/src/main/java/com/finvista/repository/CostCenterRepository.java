package com.finvista.repository;

import com.finvista.model.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CostCenterRepository
        extends JpaRepository<CostCenter, Long> {

    List<CostCenter> findAllByOrderByValorDesc();
}