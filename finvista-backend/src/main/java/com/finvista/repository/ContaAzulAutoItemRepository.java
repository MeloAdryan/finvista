package com.finvista.repository;

import com.finvista.model.ContaAzulAutoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;

public interface ContaAzulAutoItemRepository extends JpaRepository<ContaAzulAutoItem, UUID> {
    @org.springframework.data.jpa.repository.Query(value="""
        select count(*) from (
            select distinct on (lower(parcela_id)) status
            from conta_azul_auto_itens
            where cliente_id=:cliente and status in ('CONFERIR','INCLUIR','ATUALIZAR','SEM_ALTERACAO')
            order by lower(parcela_id),concluido_em desc nulls last,id desc
        ) atuais where status='CONFERIR'
        """,nativeQuery=true)
    long countPendenciasAtuais(@org.springframework.data.repository.query.Param("cliente") Long cliente);

    java.util.List<ContaAzulAutoItem> findByExecucaoIdAndClienteIdAndListadaTrue(UUID execucaoId,Long clienteId);

    @org.springframework.data.jpa.repository.Query("select i from ContaAzulAutoItem i where i.clienteId=:cliente and i.status in ('CONFERIR','INCLUIR','ATUALIZAR','SEM_ALTERACAO') order by case when i.concluidoEm is null then 1 else 0 end, i.concluidoEm desc, i.id desc")
    java.util.List<ContaAzulAutoItem> findTerminalByCliente(@org.springframework.data.repository.query.Param("cliente") Long cliente);

    Optional<ContaAzulAutoItem> findFirstByExecucaoIdAndStatusOrderByIdAsc(UUID execucao, String status);

    long countByExecucaoIdAndStatus(UUID execucao, String status);

    Page<ContaAzulAutoItem> findByExecucaoIdAndClienteIdAndStatusOrderByIdAsc(UUID execucao, Long cliente, String status, Pageable pagina);

    java.util.Optional<ContaAzulAutoItem> findFirstByClienteIdAndParcelaIdAndStatusInOrderByConcluidoEmDescIdDesc(
            Long clienteId, String parcelaId, java.util.Collection<String> status);

}
