package com.finvista.repository;

import com.finvista.model.FinancialAlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface FinancialAlertRecordRepository extends JpaRepository<FinancialAlertRecord, Long>,
        JpaSpecificationExecutor<FinancialAlertRecord> {

    List<FinancialAlertRecord> findByClienteIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
            Long clienteId, LocalDate fim, LocalDate inicio);

    Optional<FinancialAlertRecord> findByIdAndClienteId(Long id, Long clienteId);
}
