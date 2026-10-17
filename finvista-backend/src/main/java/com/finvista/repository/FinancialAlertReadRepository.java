package com.finvista.repository;

import com.finvista.model.FinancialAlertRead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FinancialAlertReadRepository extends JpaRepository<FinancialAlertRead, Long> {

    Optional<FinancialAlertRead> findByAlertaIdAndUsuarioId(Long alertaId, Long usuarioId);

    List<FinancialAlertRead> findByAlertaClienteIdAndUsuarioIdAndAlertaIdIn(Long clienteId, Long usuarioId, List<Long> alertas);
}
