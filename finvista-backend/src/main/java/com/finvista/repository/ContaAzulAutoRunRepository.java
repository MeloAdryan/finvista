package com.finvista.repository;

import com.finvista.model.ContaAzulAutoRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ContaAzulAutoRunRepository extends JpaRepository<ContaAzulAutoRun, UUID> {

    Optional<ContaAzulAutoRun> findByIdAndClienteId(UUID id, Long clienteId);

    List<ContaAzulAutoRun> findTop10ByClienteIdOrderByCriadoEmDesc(Long clienteId);
}   