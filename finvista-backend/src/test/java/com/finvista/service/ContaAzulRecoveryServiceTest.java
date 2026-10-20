package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finvista.dto.importacao.ContaAzulConferenceResponse;
import com.finvista.dto.importacao.ContaAzulConferenceResponse.*;
import com.finvista.dto.importacao.ContaAzulSyncResponse.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContaAzulRecoveryServiceTest {

    private ContaAzulConferenceService conf;
    private ExcelImportService excel;
    private FinancialTransactionRepository tx;
    private FinancialAllocationRepository rateios;
    private ContaAzulSyncBatchRepository lotes;
    private EntityManager em;
    private ClienteContextService contexto;
    private ContaAzulSyncService service;
    private Cliente cliente;
    private final LocalDate data = LocalDate.of(2026, 9, 1), corte = LocalDate.of(2026, 10, 4);
    private final MockMultipartFile file = new MockMultipartFile("arquivo", "amostra.xls", "application/octet-stream", new byte[]{1, 2, 3});

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    @BeforeEach
    void preparar() throws Exception {
        conf = mock(ContaAzulConferenceService.class);
        excel = mock(ExcelImportService.class);
        tx = mock(FinancialTransactionRepository.class);
        rateios = mock(FinancialAllocationRepository.class);
        lotes = mock(ContaAzulSyncBatchRepository.class);
        em = mock(EntityManager.class);
        contexto = mock(ClienteContextService.class);
        cliente = new Cliente("Empresa");
        ReflectionTestUtils.setField(cliente, "id", 7L);
        when(contexto.getClienteAtual()).thenReturn(cliente);
        Usuario usuario = mock(Usuario.class);
        when(usuario.getEmail()).thenReturn("teste@empresa.local");
        when(contexto.getUsuarioAutenticado()).thenReturn(usuario);
        when(em.find(Cliente.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(cliente);
        when(tx.findByClienteIdAndOrigemOrderByIdAsc(7L, "CONTA_AZUL_EXCEL")).thenReturn(List.of());
        when(rateios.findByLancamentoClienteIdAndLancamentoOrigemOrderByLancamentoIdAscBlocoAsc(7L, "CONTA_AZUL_EXCEL")).thenReturn(List.of());
        when(tx.save(any())).thenAnswer(inv -> {
            FinancialTransaction value = inv.getArgument(0);
            if (value.getId() == null) {
                ReflectionTestUtils.setField(value, "id", 99L);
            }
            return value;
        });
        when(lotes.save(any())).thenAnswer(inv -> {
            ContaAzulSyncBatch value = inv.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 50L);
            return value;
        });
        service = new ContaAzulSyncService(conf, excel, tx, rateios, lotes, contexto, em,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new ContaAzulOriginGuard(mock(ContaAzulApiOperationRepository.class), new ObjectMapper().findAndRegisterModules()));
        fonte(false);
    }

    private FinancialTransaction parcela(Long id) {
        FinancialTransaction t = new FinancialTransaction(data, "Parcela teste", "DESPESA", n("100"), "A", "Centro A", "CONTA_AZUL_EXCEL", "ref");
        t.setDataCompetencia(data);
        t.setDataVencimento(data);
        t.setValorOriginal(n("100"));
        t.setEntidadeExternaId("fornecedor-1");
        t.setCliente(cliente);
        if (id != null) {
            ReflectionTestUtils.setField(t, "id", id);
        }
        return t;
    }

    private List<Rateio> blocos() {
        return List.of(new Rateio(1, "A", n("-60"), "Centro A", n("-60")),
                new Rateio(2, "B", n("-40"), "Centro B", n("-40")));
    }

    private Linha linha(int numero, boolean pago) {
        return new Linha(numero, data, data, null, pago ? data : null, "Parcela teste", pago ? "Quitado" : "Em aberto",
                n("100"), pago ? n("100") : n("0"), pago ? n("0") : n("100"), n("0"), n("0"), n("0"),
                pago ? n("100") : n("0"), n("0"), n("0"), n("0"), pago ? n("0") : n("100"), blocos(), List.of(), List.of());
    }

    private void fonte(boolean pago) throws Exception {
        when(conf.conferir(file, corte)).thenReturn(new ContaAzulConferenceResponse("amostra.xls", "DESPESA", corte, 1, 1, 0, 0,
                n("100"), pago ? n("100") : n("0"), pago ? n("0") : n("100"), List.of(linha(2, pago))));
        when(excel.processar(file)).thenAnswer(inv -> List.of(parcela(null)));
    }

    private void existentes(FinancialTransaction... valores) {
        when(tx.findByClienteIdAndOrigemOrderByIdAsc(7L, "CONTA_AZUL_EXCEL")).thenReturn(List.of(valores));
    }


    @Test void previaSomenteLeituraIdentificaCamposAusentes() throws Exception {
        var antigo=parcela(945L);antigo.setValorRealizado(n("0"));existentes(antigo);
        var p=service.planejarRecuperacao(file,corte);assertEquals(1,p.elegiveis());
        assertEquals(945L,p.itens().get(0).lancamentoId());
        assertTrue(p.itens().get(0).campos().stream().anyMatch(c->c.campo().equals("valorAberto")&&c.valor().compareTo(n("100"))==0));
        assertNull(antigo.getValorAberto());verify(tx,never()).save(any());verify(lotes,never()).save(any());
    }
    @Test void recuperaSemAlterarIdMetadadosOuRateios() throws Exception {
        var antigo=parcela(945L);antigo.setValorRealizado(n("0"));antigo.setCategoria(null);antigo.setCentroCusto(null);
        antigo.setSituacao("Em aberto");antigo.setObservacoes("Preservar");existentes(antigo);
        var p=service.planejarRecuperacao(file,corte);var r=service.aplicarRecuperacao(file,corte,p.token(),List.of(2),true);
        assertEquals(1,r.atualizados());assertEquals(945L,antigo.getId());assertEquals(0,antigo.getValorAberto().compareTo(n("100")));
        assertEquals(n("0"),antigo.getValorRealizado());assertNull(antigo.getCategoria());assertNull(antigo.getCentroCusto());
        assertEquals(data,antigo.getData());assertEquals(data,antigo.getDataVencimento());assertEquals("Preservar",antigo.getObservacoes());
        verify(rateios,never()).deleteByLancamentoIdAndLancamentoClienteId(anyLong(),anyLong());verify(rateios,never()).saveAll(any());
        verify(tx).save(antigo);verify(lotes).save(any());assertEquals(50L,r.loteId());
    }
    @Test void divergenciaNumericaFicaForaInclusiveZeroInformado() throws Exception {
        var antigo=parcela(945L);antigo.setValorAberto(n("0"));existentes(antigo);
        var p=service.planejarRecuperacao(file,corte);assertEquals(0,p.elegiveis());
        assertTrue(p.itens().get(0).motivo().contains("diverge"));assertEquals(n("0"),antigo.getValorAberto());
    }
    @Test void naoCriaQuandoNaoHaCandidato() throws Exception {
        var p=service.planejarRecuperacao(file,corte);assertEquals(0,p.elegiveis());
        assertThrows(IllegalArgumentException.class,()->service.aplicarRecuperacao(file,corte,p.token(),List.of(2),true));verify(tx,never()).save(any());
    }
    @Test void duplicidadeDeCandidatosNaoEscolhePrimeiro() throws Exception {
        existentes(parcela(1L),parcela(2L));var p=service.planejarRecuperacao(file,corte);
        assertEquals(0,p.elegiveis());assertTrue(p.itens().get(0).motivo().contains("Mais de um"));
    }
    @Test void linhaDuplicadaDoArquivoNaoUsaMesmoIdDuasVezes() throws Exception {
        existentes(parcela(945L));
        when(conf.conferir(file,corte)).thenReturn(new ContaAzulConferenceResponse("amostra.xls","DESPESA",corte,2,2,0,0,n("200"),n("0"),n("200"),List.of(linha(2,false),linha(3,false))));
        when(excel.processar(file)).thenAnswer(i->List.of(parcela(null),parcela(null)));
        var p=service.planejarRecuperacao(file,corte);assertEquals(0,p.elegiveis());assertTrue(p.itens().stream().allMatch(i->i.motivo().contains("mais de uma linha")));
    }
    @Test void exigeContraparteEDatasCompletas() throws Exception {
        var antigo=parcela(945L);antigo.setEntidadeExternaId(null);antigo.setEntidadeNome(null);existentes(antigo);
        assertEquals(0,service.planejarRecuperacao(file,corte).elegiveis());
        antigo.setEntidadeExternaId("fornecedor-1");antigo.setDataVencimento(null);
        assertEquals(0,service.planejarRecuperacao(file,corte).elegiveis());
    }
    @Test void protegeAPI() throws Exception {
        var api=parcela(20L);api.setOrigem("CONTA_AZUL_API");
        when(tx.findByClienteIdAndOrigemOrderByIdAsc(7L,"CONTA_AZUL_API")).thenReturn(List.of(api));
        var p=service.planejarRecuperacao(file,corte);assertEquals(0,p.elegiveis());assertTrue(p.itens().get(0).motivo().contains("API"));
    }
    @Test void tokenDeOutroModoOuDadosAlteradosRejeitaAntesDeEscrever() throws Exception {
        var antigo=parcela(945L);existentes(antigo);var normal=service.planejar(file,corte);
        assertThrows(ResponseStatusException.class,()->service.aplicarRecuperacao(file,corte,normal.token(),List.of(2),true));
        var p=service.planejarRecuperacao(file,corte);antigo.setObservacoes("Mudou");
        assertThrows(ResponseStatusException.class,()->service.aplicarRecuperacao(file,corte,p.token(),List.of(2),true));verify(tx,never()).save(any());
    }
    @Test void exigeConsentimentoENaoAceitaLinhasRepetidasOuInexistentes() throws Exception {
        existentes(parcela(945L));var p=service.planejarRecuperacao(file,corte);
        assertThrows(IllegalArgumentException.class,()->service.aplicarRecuperacao(file,corte,p.token(),List.of(2),false));
        assertThrows(IllegalArgumentException.class,()->service.aplicarRecuperacao(file,corte,p.token(),List.of(2,2),true));
        assertThrows(IllegalArgumentException.class,()->service.aplicarRecuperacao(file,corte,p.token(),List.of(999),true));verify(tx,never()).save(any());
    }
    @Test void repeticaoNaoTemMaisCamposParaRecuperar() throws Exception {
        existentes(parcela(945L));var p=service.planejarRecuperacao(file,corte);service.aplicarRecuperacao(file,corte,p.token(),List.of(2),true);
        assertEquals(0,service.planejarRecuperacao(file,corte).elegiveis());
    }
    @Test void validaTodasAsLinhasAntesDeMutar() throws Exception {
        var antigo=parcela(945L);existentes(antigo);var p=service.planejarRecuperacao(file,corte);
        assertThrows(IllegalArgumentException.class,()->service.aplicarRecuperacao(file,corte,p.token(),List.of(2,99),true));assertNull(antigo.getValorAberto());verify(tx,never()).save(any());
    }
    @Test void situacaoConhecidaDivergenteImpedeRecuperacao() throws Exception {
        var antigo=parcela(945L);antigo.setSituacao("Quitado");existentes(antigo);
        assertEquals(0,service.planejarRecuperacao(file,corte).elegiveis());
    }
    @Test void somenteCamposNulosSaoPreenchidosMesmoComEscalasDiferentes() throws Exception {
        var antigo=parcela(945L);antigo.setValorAberto(n("100.00"));antigo.setJurosPrevisto(n("0.00"));existentes(antigo);
        var p=service.planejarRecuperacao(file,corte);assertTrue(p.itens().get(0).campos().stream().noneMatch(c->c.campo().equals("valorAberto")||c.campo().equals("jurosPrevisto")));
        service.aplicarRecuperacao(file,corte,p.token(),List.of(2),true);assertEquals(n("100.00"),antigo.getValorAberto());assertEquals(n("0.00"),antigo.getJurosPrevisto());
    }

    @Test void bloqueiaRegistroAntesDePreencher() throws Exception {
        var antigo=parcela(945L);existentes(antigo);var p=service.planejarRecuperacao(file,corte);
        service.aplicarRecuperacao(file,corte,p.token(),List.of(2),true);
        var ordem=inOrder(em,tx);ordem.verify(em).refresh(antigo,LockModeType.PESSIMISTIC_WRITE);ordem.verify(tx).save(antigo);
    }
    @Test void mudancaEncontradaAoRelerSobBloqueioCancelaLote() throws Exception {
        var antigo=parcela(945L);existentes(antigo);var p=service.planejarRecuperacao(file,corte);
        doAnswer(i->{antigo.setObservacoes("Alteração concorrente");return null;}).when(em).refresh(antigo,LockModeType.PESSIMISTIC_WRITE);
        assertThrows(ResponseStatusException.class,()->service.aplicarRecuperacao(file,corte,p.token(),List.of(2),true));
        verify(tx,never()).save(any());assertNull(antigo.getValorAberto());
    }
}
