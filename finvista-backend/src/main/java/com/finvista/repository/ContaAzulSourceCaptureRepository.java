package com.finvista.repository;

import com.finvista.model.ContaAzulSourceCapture;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaAzulSourceCaptureRepository extends JpaRepository<ContaAzulSourceCapture, UUID> {

    java.util.List<ContaAzulSourceCapture> findByClienteIdAndExecucaoIdOrderByTipoAscChaveAsc(Long clienteId, UUID execucaoId);

    Page<ContaAzulSourceCapture> findByClienteIdAndExecucaoIdAndTipoOrderByChaveAsc(Long clienteId, UUID execucaoId, String tipo, Pageable pagina);
}
