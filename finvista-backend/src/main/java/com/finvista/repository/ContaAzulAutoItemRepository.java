package com.finvista.repository;

import com.finvista.model.ContaAzulAutoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;

public interface ContaAzulAutoItemRepository extends JpaRepository<ContaAzulAutoItem, UUID> {

    Optional<ContaAzulAutoItem> findFirstByExecucaoIdAndStatusOrderByIdAsc(UUID execucao, String status);

    long countByExecucaoIdAndStatus(UUID execucao, String status);

    Page<ContaAzulAutoItem> findByExecucaoIdAndClienteIdAndStatusOrderByIdAsc(UUID execucao, Long cliente, String status, Pageable pagina);
}
