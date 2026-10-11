package com.finvista.repository;

import com.finvista.model.ContaAzulConnection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContaAzulConnectionRepository extends JpaRepository<ContaAzulConnection, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaAzulConnection c where c.clienteId = :id")
    Optional<ContaAzulConnection> bloquear(@Param("id") Long id);
}
