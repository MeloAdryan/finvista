package com.finvista.integration.contaazul;

import com.finvista.controller.ContaAzulApiPlanController;
import com.finvista.model.FinancialTransaction;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.service.ClienteContextService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulApiPlanTest {

    static final String ID = "9986f173-f531-4660-96ae-04b71c879264";
    static final LocalDate DIA = LocalDate.of(2026, 10, 1);
    FinancialTransactionRepository repo;
    ClienteContextService contexto;
    ContaAzulApiPlanService service;

    BigDecimal dinheiro(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    @BeforeEach
    void setup() {
        repo = mock(FinancialTransactionRepository.class);
        contexto = mock(ClienteContextService.class);
        when(contexto.getClienteAtualId()).thenReturn(1L);
        doReturn(List.of()).when(repo).findByClienteId(1L);
        service = new ContaAzulApiPlanService(repo, contexto);
    }

    ContaAzulParcelDetail.Rateio categoria(String valor, List<ContaAzulParcelDetail.Centro> centros) {
        return new ContaAzulParcelDetail.Rateio("cat", "Consulta", dinheiro(valor), dinheiro(valor), centros);
    }

    ContaAzulParcelDetail parcela(Integer quantidade, String pago, String pendente, List<ContaAzulParcelDetail.Rateio> rateios) {
        return new ContaAzulParcelDetail(ID, "Consulta", "RECEBIDO", DIA, dinheiro("100.00"), dinheiro(pago), dinheiro(pendente),
                new ContaAzulParcelDetail.Composicao(dinheiro("100"), dinheiro("100"), null, null, null, null),
                new ContaAzulParcelDetail.Evento("evento", "RECEITA", DIA, new ContaAzulParcelDetail.Condicao(quantidade), rateios));
    }

    ContaAzulParcelDetail valida() {
        return parcela(1, "100", "0", List.of(categoria("100", List.of())));
    }

    FinancialTransaction registro(String origem, String referencia, String descricao, String tipo, LocalDate data) {
        var l = mock(FinancialTransaction.class);
        when(l.getId()).thenReturn(10L);
        when(l.getOrigem()).thenReturn(origem);
        when(l.getDocumentoReferencia()).thenReturn(referencia);
        when(l.getDescricao()).thenReturn(descricao);
        when(l.getTipo()).thenReturn(tipo);
        when(l.getDataVencimento()).thenReturn(data);
        when(l.getValor()).thenReturn(dinheiro("80"));
        return l;
    }

    void bloqueado(ContaAzulParcelDetail p) {
        assertEquals(ContaAzulApiPlanService.Situacao.BLOQUEADO, service.planejar(1L, p).situacao());
    }

    @Test
    void preparaSemGravarNemConfirmarParcelaNova() {
        var p = service.planejar(1L, valida());
        assertEquals(ContaAzulApiPlanService.Situacao.SEM_CORRESPONDENCIA, p.situacao());
        assertEquals("1:CONTA_AZUL_API:" + ID, p.identidadeApi());
        assertTrue(p.avisos().get(0).contains("não comprova"));
        verify(repo).findByClienteId(1L);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void administradorObrigatorio() {
        doThrow(new IllegalStateException()).when(contexto).validarAdministrador();
        assertThrows(IllegalStateException.class, () -> service.planejar(1L, valida()));
        verifyNoInteractions(repo);
    }

    @Test
    void empresaDiferenteNaoConsultaBanco() {
        assertThrows(IllegalArgumentException.class, () -> service.planejar(2L, valida()));
        verifyNoInteractions(repo);
    }

    @Test
    void trocaDeEmpresaDuranteLeituraDescartaPlano() {
        when(contexto.getClienteAtualId()).thenReturn(1L, 2L);
        assertThrows(IllegalArgumentException.class, () -> service.planejar(1L, valida()));
    }

    @Test
    void identificaPorOrigemEIdMesmoComDescricaoAlterada() {
        doReturn(List.of(registro("CONTA_AZUL_API", ID.toUpperCase(Locale.ROOT), "Alterada", "RECEITA", DIA))).when(repo).findByClienteId(1L);
        assertEquals(ContaAzulApiPlanService.Situacao.REGISTRO_IDENTIFICADO, service.planejar(1L, valida()).situacao());
    }

    @Test
    void identidadeDuplicadaBloqueia() {
        var l = registro("CONTA_AZUL_API", ID, "Consulta", "RECEITA", DIA);
        doReturn(List.of(l, l)).when(repo).findByClienteId(1L);
        bloqueado(valida());
    }

    @Test
    void identidadeComTipoDivergenteBloqueia() {
        doReturn(List.of(registro("CONTA_AZUL_API", ID, "Consulta", "DESPESA", DIA))).when(repo).findByClienteId(1L);
        bloqueado(valida());
    }

    @Test
    void origemExcelNaoConfirmaIdentidadeMesmoComIdIgual() {
        doReturn(List.of(registro("EXCEL", ID, "Consulta", "RECEITA", DIA))).when(repo).findByClienteId(1L);
        assertEquals(ContaAzulApiPlanService.Situacao.CORRESPONDENCIA_POSSIVEL, service.planejar(1L, valida()).situacao());
    }

    @Test
    void valorDiferenteMantemPossivelCorrespondencia() {
        doReturn(List.of(registro("EXCEL", null, " Consulta ", "RECEITA", DIA))).when(repo).findByClienteId(1L);
        assertEquals(1, service.planejar(1L, valida()).totalCorrespondencias());
    }

    @Test
    void descricaoTipoOuDatasDiferentesNaoSaoCorrespondencias() {
        doReturn(List.of(registro("EXCEL", null, "Outra", "RECEITA", DIA), registro("EXCEL", null, "Consulta", "DESPESA", DIA), registro("EXCEL", null, "Consulta", "RECEITA", DIA.plusDays(1)))).when(repo).findByClienteId(1L);
        assertEquals(0, service.planejar(1L, valida()).totalCorrespondencias());
    }

    @Test
    void competenciaIgualTambemExigeConferencia() {
        var l = registro("EXCEL", null, "Consulta", "RECEITA", null);
        when(l.getDataCompetencia()).thenReturn(DIA);
        doReturn(List.of(l)).when(repo).findByClienteId(1L);
        assertEquals(ContaAzulApiPlanService.Situacao.CORRESPONDENCIA_POSSIVEL, service.planejar(1L, valida()).situacao());
    }

    @Test
    void limitaExibicaoSemEsconderContagem() {
        var l = registro("EXCEL", null, "Consulta", "RECEITA", DIA);
        doReturn(Collections.nCopies(21, l)).when(repo).findByClienteId(1L);
        var p = service.planejar(1L, valida());
        assertEquals(21, p.totalCorrespondencias());
        assertEquals(20, p.correspondencias().size());
    }

    @Test
    void variasParcelasNaoRecebemDivisaoPresumida() {
        bloqueado(parcela(2, "100", "0", List.of(categoria("100", List.of()))));
    }

    @Test
    void quantidadeAusenteNaoEquivaleAUma() {
        bloqueado(parcela(null, "100", "0", List.of(categoria("100", List.of()))));
    }

    @Test
    void pagoAusenteNaoEquivaleAZero() {
        bloqueado(parcela(1, null, "0", List.of(categoria("100", List.of()))));
    }

    @Test
    void pagoMaisPendentePrecisaConferir() {
        bloqueado(parcela(1, "99", "0", List.of(categoria("100", List.of()))));
    }

    @Test
    void pagoNegativoBloqueia() {
        bloqueado(parcela(1, "-1", "101", List.of(categoria("100", List.of()))));
    }

    @Test
    void precisaoExcessivaBloqueia() {
        bloqueado(parcela(1, "99.999", "0.001", List.of(categoria("100", List.of()))));
    }

    @Test
    void valorForaDaColunaBloqueia() {
        bloqueado(parcela(1, "10000000000000", "0", List.of(categoria("100", List.of()))));
    }

    @Test
    void categoriasDevemSomarValorDaParcela() {
        bloqueado(parcela(1, "100", "0", List.of(categoria("90", List.of()))));
    }

    @Test
    void categoriasDuplicadasBloqueiam() {
        bloqueado(parcela(1, "100", "0", List.of(categoria("50", List.of()), categoria("50", List.of()))));
    }

    @Test
    void centrosAusentesDiferemDeListaVazia() {
        bloqueado(parcela(1, "100", "0", List.of(categoria("100", null))));
        assertEquals(ContaAzulApiPlanService.Situacao.SEM_CORRESPONDENCIA, service.planejar(1L, valida()).situacao());
    }

    @Test
    void centroSemBrutoNaoRecebeValorPresumido() {
        bloqueado(parcela(1, "100", "0", List.of(categoria("100", List.of(new ContaAzulParcelDetail.Centro("c", "Centro", dinheiro("100"), null))))));
    }

    @Test
    void centrosDevemSomarCategoria() {
        bloqueado(parcela(1, "100", "0", List.of(categoria("100", List.of(new ContaAzulParcelDetail.Centro("c", "Centro", dinheiro("90"), dinheiro("90")))))));
    }

    @Test
    void centrosDuplicadosBloqueiam() {
        var c = new ContaAzulParcelDetail.Centro("c", "Centro", dinheiro("50"), dinheiro("50"));
        bloqueado(parcela(1, "100", "0", List.of(categoria("100", List.of(c, c)))));
    }

    @Test
    void centrosValidosComEscalasDiferentesConferem() {
        var c = new ContaAzulParcelDetail.Centro("c", "Centro", dinheiro("100.0"), dinheiro("100.00"));
        assertEquals(ContaAzulApiPlanService.Situacao.SEM_CORRESPONDENCIA, service.planejar(1L, parcela(1, "100", "0", List.of(categoria("100", List.of(c))))).situacao());
    }

    @Test
    void eventoAusenteBloqueia() {
        var p = valida();
        bloqueado(new ContaAzulParcelDetail(ID, p.descricao(), p.status(), DIA, p.liquido(), p.pago(), p.pendente(), p.composicao(), null));
    }

    @Test
    void statusCanceladoExigeConferencia() {
        var p = valida();
        bloqueado(new ContaAzulParcelDetail(ID, p.descricao(), "CANCELADO", DIA, p.liquido(), p.pago(), p.pendente(), p.composicao(), p.evento()));
    }

    @Test
    void controllerReconsultaFonteAntesDoPlano() {
        var conexoes = mock(ContaAzulConnectionService.class);
        var detalhes = mock(ContaAzulParcelDetailClient.class);
        var planos = mock(ContaAzulApiPlanService.class);
        when(conexoes.acessoParaConsulta(1L)).thenReturn("token");
        var p = valida();
        when(detalhes.consultar("token", ID)).thenReturn(p);
        var controller = new ContaAzulApiPlanController(conexoes, detalhes, planos, contexto);
        var response = controller.planejar(ID, 1L);
        assertEquals("no-store", response.getHeaders().getCacheControl());
        var ordem = inOrder(conexoes, detalhes, planos);
        ordem.verify(conexoes).acessoParaConsulta(1L);
        ordem.verify(detalhes).consultar("token", ID);
        ordem.verify(planos).planejar(1L, p);
    }

    @Test
    void controllerRejeitaIdInvalidoAntesDeAcessarFonte() {
        var conexoes = mock(ContaAzulConnectionService.class);
        var detalhes = mock(ContaAzulParcelDetailClient.class);
        var planos = mock(ContaAzulApiPlanService.class);
        var controller = new ContaAzulApiPlanController(conexoes, detalhes, planos, contexto);
        assertThrows(IllegalArgumentException.class, () -> controller.planejar("id-invalido", 1L));
        verifyNoInteractions(conexoes, detalhes, planos);
    }
}
