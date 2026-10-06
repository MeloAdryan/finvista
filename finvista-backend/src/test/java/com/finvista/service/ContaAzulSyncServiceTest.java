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

class ContaAzulSyncServiceTest {

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
                new ObjectMapper().registerModule(new JavaTimeModule()));
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

    @Test
    void planejaSemGravarEConsultaSomenteClienteAtual() throws Exception {
        existentes(parcela(10L));
        Plano plano = service.planejar(file, corte);
        assertEquals(List.of(10L), plano.itens().get(0).candidatos());
        verify(tx, never()).save(any());
        verifyNoInteractions(lotes);
        verify(tx).findByClienteIdAndOrigemOrderByIdAsc(7L, "CONTA_AZUL_EXCEL");
    }

    @Test
    void criaParcelaComDoisRateiosERegistraAuditoria() throws Exception {
        Plano plano = service.planejar(file, corte);
        Resultado r = service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "CRIAR", null)));
        assertEquals(1, r.criados());
        assertEquals(0, r.pendentes());
        assertEquals(50L, r.loteId());
        verify(em).find(Cliente.class, 7L, LockModeType.PESSIMISTIC_WRITE);
        verify(rateios).saveAll(argThat((Iterable<FinancialAllocation> lista) -> {
            List<FinancialAllocation> l = new ArrayList<>();
            lista.forEach(l::add);
            return l.size() == 2 && l.get(1).getValorCategoria().compareTo(n("-40")) == 0
                    && l.get(1).getLancamento().getId().equals(99L);
        }));
        verify(lotes).save(argThat(l -> l.getAuditoria().contains("depois")));
    }

    @Test
    void atualizaPagoSemCriarOutraParcelaEPreservaId() throws Exception {
        FinancialTransaction antiga = parcela(10L);
        antiga.setValorRealizado(n("0"));
        antiga.setValorAberto(n("100"));
        existentes(antiga);
        fonte(true);
        Plano plano = service.planejar(file, corte);
        Resultado r = service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "ATUALIZAR", 10L)));
        assertEquals(0, r.criados());
        assertEquals(1, r.atualizados());
        assertEquals(10L, antiga.getId());
        assertEquals(0, antiga.getValorAberto().compareTo(n("0")));
        assertEquals(0, antiga.getValorTotalRealizado().compareTo(n("100")));
        verify(rateios).deleteByLancamentoIdAndLancamentoClienteId(10L, 7L);
        verify(tx).save(same(antiga));
    }

    @Test
    void repeticaoDoArquivoMantemParcelaERateiosSemRecriar() throws Exception {
        Plano plano = service.planejar(file, corte);
        service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "CRIAR", null)));
        var captura = org.mockito.ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(tx).save(captura.capture());
        FinancialTransaction salvo = captura.getValue();
        existentes(salvo);
        when(rateios.findByLancamentoClienteIdAndLancamentoOrigemOrderByLancamentoIdAscBlocoAsc(7L, "CONTA_AZUL_EXCEL"))
                .thenReturn(List.of(new FinancialAllocation(salvo, 1, "A", n("-60.00"), "Centro A", n("-60.00")),
                        new FinancialAllocation(salvo, 2, "B", n("-40.00"), "Centro B", n("-40.00"))));
        clearInvocations(tx, rateios);
        Plano novo = service.planejar(file, corte);
        Resultado r = service.aplicar(file, corte, novo.token(), List.of(new Decisao(2, "ATUALIZAR", 99L)));
        assertEquals(1, r.mantidos());
        assertEquals(0, r.criados());
        assertEquals(0, r.atualizados());
        verify(tx, never()).save(any());
        verify(rateios, never()).deleteByLancamentoIdAndLancamentoClienteId(anyLong(), anyLong());
    }

    @Test
    void ambiguidadeSemDecisaoPermanecePendente() throws Exception {
        existentes(parcela(10L), parcela(11L));
        Plano plano = service.planejar(file, corte);
        assertEquals("AMBIGUO", plano.itens().get(0).sugestao());
        Resultado r = service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "IGNORAR", null)));
        assertEquals(1, r.pendentes());
        verify(tx, never()).save(any());
    }

    @Test
    void naoPermiteCriarQuandoHaCandidatoNemIdDeOutroCliente() throws Exception {
        existentes(parcela(10L));
        Plano plano = service.planejar(file, corte);
        assertThrows(IllegalArgumentException.class, () -> service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "CRIAR", null))));
        assertThrows(IllegalArgumentException.class, () -> service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "ATUALIZAR", 800L))));
        verify(tx, never()).save(any());
        verifyNoInteractions(lotes);
    }

    @Test
    void planoDesatualizadoBloqueiaAntesDeGravar() throws Exception {
        FinancialTransaction antigo = parcela(10L);
        existentes(antigo);
        Plano plano = service.planejar(file, corte);
        antigo.setSituacao("Quitado");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "ATUALIZAR", 10L))));
        assertEquals(409, e.getStatusCode().value());
        verify(tx, never()).save(any());
    }

    @Test
    void validaTodasAsDecisoesAntesDeAlterarEntidades() throws Exception {
        FinancialTransaction antigo = parcela(10L);
        antigo.setValorAberto(n("100"));
        existentes(antigo);
        fonte(true);
        Plano plano = service.planejar(file, corte);
        assertThrows(IllegalArgumentException.class, () -> service.aplicar(file, corte, plano.token(),
                List.of(new Decisao(2, "ATUALIZAR", 10L), new Decisao(999, "CRIAR", null))));
        assertEquals(0, antigo.getValorAberto().compareTo(n("100")));
        verify(tx, never()).save(any());
    }

    @Test
    void naoUneLinhasIguaisDoMesmoArquivo() throws Exception {
        when(conf.conferir(file, corte)).thenReturn(new ContaAzulConferenceResponse("amostra.xls", "DESPESA", corte, 2, 2, 0, 0,
                n("200"), n("0"), n("200"), List.of(linha(2, false), linha(3, false))));
        when(excel.processar(file)).thenAnswer(inv -> List.of(parcela(null), parcela(null)));
        Plano plano = service.planejar(file, corte);
        assertEquals(2, plano.itens().size());
        Resultado r = service.aplicar(file, corte, plano.token(), List.of(new Decisao(2, "CRIAR", null), new Decisao(3, "CRIAR", null)));
        assertEquals(2, r.criados());
        verify(tx, times(2)).save(any());
    }

    @Test
    void bloqueiaUsoDoMesmoIdEmDuasLinhas() throws Exception {
        existentes(parcela(10L));
        when(conf.conferir(file, corte)).thenReturn(new ContaAzulConferenceResponse("amostra.xls", "DESPESA", corte, 2, 2, 0, 0,
                n("200"), n("0"), n("200"), List.of(linha(2, false), linha(3, false))));
        when(excel.processar(file)).thenAnswer(inv -> List.of(parcela(null), parcela(null)));
        Plano plano = service.planejar(file, corte);
        assertThrows(IllegalArgumentException.class, () -> service.aplicar(file, corte, plano.token(),
                List.of(new Decisao(2, "ATUALIZAR", 10L), new Decisao(3, "ATUALIZAR", 10L))));
        verify(tx, never()).save(any());
    }

    @Test
    void consultaCandidatoDoClienteAtual() {
        existentes(parcela(10L));
        var candidato = service.consultarCandidato(10L);
        assertEquals(10L, candidato.id());
        assertEquals("Parcela teste", candidato.descricao());
        verify(tx).findByClienteIdAndOrigemOrderByIdAsc(7L, "CONTA_AZUL_EXCEL");
    }

    @Test
    void naoExibeCandidatoForaDoConjuntoDoCliente() {
        existentes(parcela(10L));
        var erro = assertThrows(ResponseStatusException.class, () -> service.consultarCandidato(800L));
        assertEquals(404, erro.getStatusCode().value());
    }
}

     
     