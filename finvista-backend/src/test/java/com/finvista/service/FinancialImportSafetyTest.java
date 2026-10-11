package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.FinancialTransactionRepository;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialImportSafetyTest {

    FinancialTransactionRepository repo;
    ClienteContextService context;
    EntityManager em;
    Cliente cliente;
    FinancialImportService service;

    @BeforeEach
    void preparar() {
        repo = mock(FinancialTransactionRepository.class);
        context = mock(ClienteContextService.class);
        em = mock(EntityManager.class);
        cliente = mock(Cliente.class);
        when(cliente.getId()).thenReturn(7L);
        when(cliente.getAtivo()).thenReturn(true);
        when(context.getClienteAtual()).thenReturn(cliente);
        when(em.find(Cliente.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(cliente);
        when(repo.findAllByClienteIdOrderByDataDesc(7L)).thenReturn(List.of());
        when(repo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        service = new FinancialImportService(repo, context, em);
    }

    FinancialTransaction linha(String documento, String categoria) {
        return new FinancialTransaction(LocalDate.of(2026, 10, 1), "Serviço", "DESPESA",
                new BigDecimal("100.00"), categoria, "Operações", "CSV", documento);
    }

    @Test
    void repeticaoIdenticaRejeitaArquivoInteiro() {
        var erro = assertThrows(IllegalArgumentException.class,
                () -> service.salvar(List.of(linha("A", "Geral"), linha("A", "Geral"))));
        assertTrue(erro.getMessage().contains("Nenhum lançamento"));
        verifyNoInteractions(repo);
    }

    @Test
    void documentosECategoriasDistintosSaoPreservados() {
        var result = service.salvar(List.of(linha("A", "Geral"), linha("B", "Geral"), linha("A", "Especial")));
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(t -> t.getCliente() == cliente));
    }

    @Test
    void centroCustoDiferenteNaoColapsaOperacoes() {
        var a = linha("A", "Geral");
        var b = linha("A", "Geral");
        b.setCentroCusto("Administrativo");
        assertEquals(2, service.salvar(List.of(a, b)).size());
    }

    @Test
    void reimportacaoIgnoraExistenteComEscalaMonetariaDiferente() {
        var existente = linha("A", "Geral");
        existente.setValor(new BigDecimal("100.0"));
        when(repo.findAllByClienteIdOrderByDataDesc(7L)).thenReturn(List.of(existente));
        assertEquals(0, service.salvar(List.of(linha("A", "Geral"))).size());
        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void bloqueioAconteceAntesDaConsultaEGravacao() {
        service.salvar(List.of(linha("A", "Geral")));
        var ordem = inOrder(em, repo);
        ordem.verify(em).find(Cliente.class, 7L, LockModeType.PESSIMISTIC_WRITE);
        ordem.verify(repo).findAllByClienteIdOrderByDataDesc(7L);
        ordem.verify(repo).saveAll(anyList());
    }

    @Test
    void empresaInativaAposSelecaoNaoGrava() {
        when(cliente.getAtivo()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.salvar(List.of(linha("A", "Geral"))));
        verifyNoInteractions(repo);
    }

    @Test
    void contaAzulMantemMultiplicidadeLegitima() {
        var a = linha("A", "Geral");
        var b = linha("A", "Geral");
        a.setOrigem("CONTA_AZUL_EXCEL");
        b.setOrigem("CONTA_AZUL_EXCEL");
        when(repo.findByClienteIdAndOrigemOrderByIdAsc(7L, "CONTA_AZUL_EXCEL")).thenReturn(List.of());
        assertEquals(2, service.salvar(List.of(a, b)).size());
    }

    @Test
    void contaAzulReimportadoNaoDuplicaMultiplicidade() {
        var a = linha("A", "Geral");
        var b = linha("A", "Geral");
        a.setOrigem("CONTA_AZUL_EXCEL");
        b.setOrigem("CONTA_AZUL_EXCEL");
        when(repo.findByClienteIdAndOrigemOrderByIdAsc(7L, "CONTA_AZUL_EXCEL")).thenReturn(List.of(a, b));
        assertEquals(0, service.salvar(List.of(a, b)).size());
        verify(repo, never()).saveAll(anyList());
    }
}
