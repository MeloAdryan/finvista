package com.finvista.repository;

import com.finvista.model.ContaAzulApiOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;



      public interface ContaAzulApiOperationRepository extends JpaRepository<ContaAzulApiOperation, UUID> {

        @org.springframework.data.jpa.repository.Query("""
        select o from ContaAzulApiOperation o where o.cliente.id=:cliente
        and o.concluidoEm=(select max(n.concluidoEm) from ContaAzulApiOperation n
            where n.cliente.id=:cliente and n.lancamentoId=o.lancamentoId)
        order by o.id desc
        """)
        List<ContaAzulApiOperation> snapshotsAtuais(@org.springframework.data.repository.query.Param("cliente") Long cliente);

        Optional<ContaAzulApiOperation> findFirstByClienteIdAndLancamentoIdOrderByConcluidoEmDescIdDesc(Long cliente, Long lancamento);

        Optional<ContaAzulApiOperation> findByIdAndClienteId(UUID id, Long clienteId);

        List<ContaAzulApiOperation> findByClienteIdAndAcaoOrderByConcluidoEmAsc(Long clienteId, String acao);
    }
