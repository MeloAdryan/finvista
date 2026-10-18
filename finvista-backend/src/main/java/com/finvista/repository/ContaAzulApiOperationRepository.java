package com.finvista.repository;

import com.finvista.model.ContaAzulApiOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ContaAzulApiOperationRepository extends JpaRepository<ContaAzulApiOperation, UUID> {

    Optional<ContaAzulApiOperation> findByIdAndClienteId(UUID id, Long clienteId);

    List<ContaAzulApiOperation> findByClienteIdAndAcaoOrderByConcluidoEmAsc(Long clienteId, String acao);
}
