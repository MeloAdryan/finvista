package com.finvista.repository;

import com.finvista.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository
        extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByIdAndAtivoTrue(
            Long id
    );

    List<Cliente> findAllByAtivoTrueOrderByNomeAsc();
}