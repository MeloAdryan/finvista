package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.*;
import com.finvista.repository.ContaAzulApiOperationRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulOriginGuardTest {
    private final ContaAzulApiOperationRepository repo=mock(ContaAzulApiOperationRepository.class);
    private final ContaAzulOriginGuard guard=new ContaAzulOriginGuard(repo,new ObjectMapper());

    private FinancialTransaction tx(String descricao,String valor) {
        var t=new FinancialTransaction(LocalDate.parse("2026-09-21"),descricao,"RECEITA",new BigDecimal(valor),"Categoria",null,"CONTA_AZUL_API","uuid-api");
        t.setDataCompetencia(t.getData());t.setDataVencimento(LocalDate.parse("2026-10-21"));
        ReflectionTestUtils.setField(t,"id",100L);
        return t;
    }

    @Test void historicoProtegeArquivoAntigoMesmoDepoisDeAtualizacaoDaAPI() {
        var op=new ContaAzulApiOperation(UUID.randomUUID(),new Cliente("Teste"),3L,"uuid","VINCULAR_EXCEL",100L,Instant.now(),
                "{\"origem\":\"CONTA_AZUL_EXCEL\",\"tipo\":\"RECEITA\",\"descricao\":\"Venda antiga\",\"dataCompetencia\":\"2026-09-21\",\"dataVencimento\":\"2026-10-21\",\"valorOriginal\":50,\"documentoReferencia\":\"ref-antiga\"}","{}","{}");
        when(repo.findByClienteIdAndAcaoOrderByConcluidoEmAsc(7L,"VINCULAR_EXCEL")).thenReturn(List.of(op));
        var api=tx("Descrição nova","80");api.setDataCompetencia(LocalDate.parse("2026-08-01"));api.setDataVencimento(LocalDate.parse("2026-08-10"));
        var arquivo=tx("Venda antiga","50");arquivo.setDocumentoReferencia("ref-antiga");
        assertTrue(guard.proteger(arquivo,api,guard.historico(7L)));
        assertFalse(guard.proteger(arquivo,api,guard.historico(8L)));
    }

    @Test void falhaDeAuditoriaBloqueiaConferenciaEmVezDeIgnorarHistorico() {
        var op=new ContaAzulApiOperation(UUID.randomUUID(),new Cliente("Teste"),3L,"uuid","VINCULAR_EXCEL",100L,Instant.now(),"inválido","{}","{}");
        when(repo.findByClienteIdAndAcaoOrderByConcluidoEmAsc(7L,"VINCULAR_EXCEL")).thenReturn(List.of(op));
        assertThrows(IllegalStateException.class,()->guard.historico(7L));
    }

    @Test void buscaConservadoraProtegeValorEDataMesmoComDescricaoDiferente() {
        assertTrue(guard.proteger(tx("Texto do Excel","50"),tx("Texto da API","50"),Map.of()));
    }

    @Test void tipoDiferenteNaoSeTornaCorrespondencia() {
        var entrada=tx("Venda","50");entrada.setTipo("DESPESA");
        assertFalse(guard.proteger(entrada,tx("Venda","50"),Map.of()));
    }

    @Test void referenciaDeContraparteSozinhaNaoBloqueiaOutraOperacao() {
        var entrada=tx("Outro serviço","200");entrada.setDataCompetencia(LocalDate.parse("2026-12-01"));entrada.setDataVencimento(LocalDate.parse("2026-12-20"));
        entrada.setDocumentoReferencia("mesmo-fornecedor");
        var api=tx("Venda","50");api.setDocumentoReferencia("mesmo-fornecedor");
        assertFalse(guard.proteger(entrada,api,Map.of()));
    }
}
