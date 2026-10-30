package com.finvista.repository;

import com.finvista.model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;



      public interface UsuarioRepository
            extends JpaRepository<Usuario, Long> {

        @EntityGraph(attributePaths = "cliente")
        Optional<Usuario> findByEmailIgnoreCase(
                String email
        );

        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("select u from Usuario u where lower(u.email) = lower(:email)")
        Optional<Usuario> bloquearPorEmail(@org.springframework.data.repository.query.Param("email") String email);

        boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

        boolean existsByEmailIgnoreCase(
                String email
        );
    }

    
    
