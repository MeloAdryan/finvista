package com.finvista.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finvista.model.Cliente;

public interface ClienteRepository
        extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByIdAndAtivoTrue(
            Long id
    );

    List<Cliente> findAllByAtivoTrueOrderByNomeAsc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cliente c where c.id = :id and c.ativo = true")
    Optional<Cliente> bloquearAtivoPorId(@org.springframework.data.repository.query.Param("id") Long id);
}
