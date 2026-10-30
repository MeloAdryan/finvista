package com.finvista.repository;

import com.finvista.model.ContaAzulAutoRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;



      public interface ContaAzulAutoRunRepository extends JpaRepository<ContaAzulAutoRun, UUID> {
        Optional<ContaAzulAutoRun> findFirstByClienteIdAndStatusInOrderByConcluidoEmDesc(Long clienteId, java.util.Collection<String> status);
        Optional<ContaAzulAutoRun> findFirstByClienteIdAndSincronizacaoCompletaTrueAndStatusInOrderByConcluidoEmDesc(Long clienteId, java.util.Collection<String> status);

        org.springframework.data.domain.Page<ContaAzulAutoRun> findByClienteIdOrderByCriadoEmDesc(Long clienteId, org.springframework.data.domain.Pageable pagina);

        Optional<ContaAzulAutoRun> findByIdAndClienteId(UUID id, Long clienteId);

        List<ContaAzulAutoRun> findTop10ByClienteIdOrderByCriadoEmDesc(Long clienteId);
    
        @org.springframework.data.jpa.repository.Query("select r from ContaAzulAutoRun r where r.clienteId = :cliente and r.sincronizacaoCompleta = true and (r.incremental = false or r.incremental is null) and r.status in ('CONCLUIDA','CONCLUIDA_COM_PENDENCIAS') and r.concluidoEm <= :ate order by r.concluidoEm desc, r.id desc")
        org.springframework.data.domain.Page<ContaAzulAutoRun> basesHistoricas(@org.springframework.data.repository.query.Param("cliente") Long cliente, @org.springframework.data.repository.query.Param("ate") java.time.Instant ate, org.springframework.data.domain.Pageable pagina);

        @org.springframework.data.jpa.repository.Query("select r from ContaAzulAutoRun r where r.clienteId = :cliente and r.sincronizacaoCompleta = true and r.status in ('CONCLUIDA','CONCLUIDA_COM_PENDENCIAS') and r.concluidoEm >= :desde and r.concluidoEm <= :ate order by r.concluidoEm asc, r.id asc")
        org.springframework.data.domain.Page<ContaAzulAutoRun> historicoConcluido(@org.springframework.data.repository.query.Param("cliente") Long cliente, @org.springframework.data.repository.query.Param("desde") java.time.Instant desde, @org.springframework.data.repository.query.Param("ate") java.time.Instant ate, org.springframework.data.domain.Pageable pagina);
}
