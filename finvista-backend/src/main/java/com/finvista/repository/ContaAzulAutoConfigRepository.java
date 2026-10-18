package com.finvista.repository;

import com.finvista.model.ContaAzulAutoConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;

public interface ContaAzulAutoConfigRepository extends JpaRepository<ContaAzulAutoConfig, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaAzulAutoConfig c where c.clienteId=:id")
    Optional<ContaAzulAutoConfig> bloquear(@Param("id") Long id);

    @Query("select c.clienteId from ContaAzulAutoConfig c where c.habilitada=true and c.proximaEm<=:agora and (c.reservaAte is null or c.reservaAte<=:agora) order by c.proximaEm,c.clienteId")
    List<Long> disponiveis(@Param("agora") Instant agora, Pageable pagina);
}
