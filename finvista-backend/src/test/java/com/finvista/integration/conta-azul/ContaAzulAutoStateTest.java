package com.finvista.integration.contaazul;

import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.ClienteContextService;
import com.finvista.dto.ContaAzulAutoResponse.*;
import com.finvista.dto.ContaAzulApiSyncResponse.Resultado;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContaAzulAutoStateTest {

    private final ContaAzulAutoConfigRepository configs = mock(ContaAzulAutoConfigRepository.class);
    private final ContaAzulAutoRunRepository runs = mock(ContaAzulAutoRunRepository.class);
    private final ContaAzulAutoItemRepository itens = mock(ContaAzulAutoItemRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final FinancialTransactionRepository tx = mock(FinancialTransactionRepository.class);
    private final ContaAzulApiSyncService sync = mock(ContaAzulApiSyncService.class);
    private final ClienteContextService contexto = mock(ClienteContextService.class);
    private final ContaAzulConfiguration integracao = mock(ContaAzulConfiguration.class);
    private final Clock clock = mock(Clock.class);
    private final Map<UUID, ContaAzulAutoRun> execucoes = new HashMap<>();
    private final Map<UUID, ContaAzulAutoItem> fila = new HashMap<>();
    private final String id = "9986f173-f531-4660-96ae-04b71c879264";
    private Instant agora = Instant.parse("2026-10-17T12:00:00Z");
    private ContaAzulAutoConfig config;
    private ContaAzulAutoState state;
    private Cliente cliente;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenAnswer(i -> agora);
        when(clock.withZone(any())).thenReturn(Clock.fixed(agora, ZoneId.of("America/Sao_Paulo")));
        cliente = new Cliente("Empresa de teste");
        ReflectionTestUtils.setField(cliente, "id", 7L);
        usuario = new Usuario("Administrador", "teste@example.test", "unused", "ADMIN");
        ReflectionTestUtils.setField(usuario, "id", 3L);
        when(clientes.findByIdAndAtivoTrue(7L)).thenReturn(Optional.of(cliente));
        when(clientes.bloquearAtivoPorId(7L)).thenReturn(Optional.of(cliente));
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(contexto.getClienteAtualId()).thenReturn(7L);
        when(contexto.getUsuarioAutenticado()).thenReturn(usuario);
        config = new ContaAzulAutoConfig();
        config.clienteId = 7L;
        config.habilitada = true;
        config.incluirNovas = false;
        config.inicio = LocalDate.parse("2026-09-01");
        config.fim = LocalDate.parse("2026-10-31");
        config.intervaloMinutos = 30;
        config.autorizadoPor = 3L;
        config.revisao = UUID.randomUUID();
        config.proximaEm = agora;
        when(configs.bloquear(7L)).thenAnswer(i -> Optional.of(config));
        when(configs.findById(7L)).thenAnswer(i -> Optional.of(config));
        when(configs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(runs.saveAndFlush(any())).thenAnswer(i -> {
            ContaAzulAutoRun r = i.getArgument(0);
            execucoes.put(r.id, r);
            return r;
        });
        when(runs.findByIdAndClienteId(any(), eq(7L))).thenAnswer(i -> Optional.ofNullable(execucoes.get(i.getArgument(0))));
        when(runs.findTop10ByClienteIdOrderByCriadoEmDesc(7L)).thenAnswer(i -> execucoes.values().stream().sorted(Comparator.comparing((ContaAzulAutoRun r) -> r.criadoEm).reversed()).toList());
        when(itens.findById(any())).thenAnswer(i -> Optional.ofNullable(fila.get(i.getArgument(0))));
        when(itens.save(any())).thenAnswer(i -> {
            ContaAzulAutoItem item = i.getArgument(0);
            fila.put(item.id, item);
            return item;
        });
        when(itens.findFirstByExecucaoIdAndStatusOrderByIdAsc(any(), any())).thenAnswer(i -> fila.values().stream()
                .filter(t -> t.execucaoId.equals(i.getArgument(0)) && t.status.equals(i.getArgument(1))).sorted(Comparator.comparing(t -> t.id)).findFirst());
        when(itens.countByExecucaoIdAndStatus(any(), any())).thenAnswer(i -> fila.values().stream().filter(t -> t.execucaoId.equals(i.getArgument(0)) && t.status.equals(i.getArgument(1))).count());
        when(itens.findByExecucaoIdAndClienteIdAndStatusOrderByIdAsc(any(), eq(7L), any(), any())).thenAnswer(i -> {
            Pageable pagina = i.getArgument(3);
            var lista = fila.values().stream().filter(t -> t.execucaoId.equals(i.getArgument(0)) && t.status.equals(i.getArgument(2))).toList();
            int inicio = (int) Math.min(pagina.getOffset(), lista.size()), fim = Math.min(lista.size(), inicio + pagina.getPageSize());
            return new PageImpl<>(lista.subList(inicio, fim), pagina, lista.size());
        });
        state = new ContaAzulAutoState(configs, runs, itens, clientes, usuarios, tx, sync, contexto, integracao, clock, true);
    }

    private Configurar pedido(boolean confirmado, boolean novas, boolean historico) {
        return new Configurar(7L, true, config.inicio, config.fim, 30, novas, confirmado, historico);
    }

    private ContaAzulFinancialClient.Parcela resumo(String id) {
        return new ContaAzulFinancialClient.Parcela(id, "Venda", LocalDate.parse("2026-10-21"), LocalDate.parse("2026-09-21"), "PENDENTE", "EM_ABERTO", null, null, null, List.of(), List.of());
    }

    private ContaAzulAutoState.Reserva comItem() {
        var r = state.assumir(7L);
        state.pagina(r, new ContaAzulFinancialClient.Pagina(1, List.of(resumo(id))));
        return r;
    }

    private ContaAzulParcelDetail detalhe() {
        return new ContaAzulParcelDetail(id, "Venda", "PENDENTE", LocalDate.parse("2026-10-21"), null, null, null, null, new ContaAzulParcelDetail.Evento("evento", "RECEITA", LocalDate.parse("2026-09-21"), new ContaAzulParcelDetail.Condicao(1), List.of()));
    }

    private ContaAzulAutoRun atual() {
        return execucoes.get(config.execucaoId);
    }

    @Test
    void exigeConfirmacaoDeAtivacaoEHistoricoParaNovasInclusoes() {
        assertThrows(IllegalArgumentException.class, () -> state.configurar(pedido(false, false, false)));
        assertThrows(IllegalArgumentException.class, () -> state.configurar(pedido(true, true, false)));
        state.configurar(pedido(true, true, true));
        assertTrue(config.habilitada);
        assertTrue(config.incluirNovas);
    }

    @Test
    void rejeitaOutraEmpresaEPeriodoExcessivo() {
        assertThrows(IllegalArgumentException.class, () -> state.configurar(new Configurar(8L, true, config.inicio, config.fim, 30, false, true, false)));
        assertThrows(IllegalArgumentException.class, () -> state.configurar(new Configurar(7L, true, config.inicio, config.inicio.plusDays(366), 30, false, true, false)));
    }

    @Test
    void empresaPausadaNaoEhAssumida() {
        config.habilitada = false;
        assertNull(state.assumir(7L));
        verifyNoInteractions(sync);
    }

    @Test
    void segundaInstanciaNaoAssumeReservaVigente() {
        assertNotNull(state.assumir(7L));
        assertNull(state.assumir(7L));
        assertEquals(1, execucoes.size());
    }

    @Test
    void reservaExpiradaEhRetomadaComMesmoRunEOwnerNovo() {
        var antiga = comItem();
        agora = agora.plusSeconds(121);
        var nova = state.assumir(7L);
        assertEquals(antiga.execucaoId(), nova.execucaoId());
        assertNotEquals(antiga.dono(), nova.dono());
        assertThrows(ContaAzulAutoState.ReservaPerdida.class, () -> state.aplicar(antiga, fila.values().iterator().next().id, detalhe()));
        assertNotNull(state.proximo(nova));
        verifyNoInteractions(sync);
    }

    @Test
    void pausaImpedeConfirmarResultadoEmTransito() {
        var r = comItem();
        state.pausar(7L);
        assertThrows(ContaAzulAutoState.ReservaPerdida.class, () -> state.aplicar(r, fila.values().iterator().next().id, detalhe()));
        assertEquals("CANCELADA", atual().status);
        verifyNoInteractions(sync);
    }

    @Test
    void alteracaoDaAutorizacaoCancelaRunAnterior() {
        var r = comItem();
        state.configurar(pedido(true, false, false));
        assertEquals("CANCELADA", execucoes.get(r.execucaoId()).status);
        assertThrows(ContaAzulAutoState.ReservaPerdida.class, () -> state.proximo(r));
    }

    @Test
    void administradorInativoDesativaAutomacao() {
        usuario.setAtivo(false);
        assertNull(state.assumir(7L));
        assertFalse(config.habilitada);
        verifyNoInteractions(sync);
    }

    @Test
    void aplicaUmaVezComIdentificadorPersistenteEContaSemNovasInclusoes() {
        var r = comItem();
        var item = state.proximo(r);
        when(sync.aplicarAutomatico(cliente, 3L, item.id, detalhe(), false)).thenReturn(new Resultado(item.id, 7L, id, "ATUALIZAR", 100L, agora));
        state.aplicar(r, item.id, detalhe());
        state.aplicar(r, item.id, detalhe());
        assertEquals(1, atual().atualizadas);
        assertEquals(100L, item.lancamentoId);
        verify(sync, times(1)).aplicarAutomatico(cliente, 3L, item.id, detalhe(), false);
    }

    @Test
    void respostaDeOutraParcelaNaoChegaAoDominio() {
        var r = comItem();
        var item = state.proximo(r);
        assertThrows(ResponseStatusException.class, () -> state.aplicar(r, item.id, new ContaAzulParcelDetail(UUID.randomUUID().toString(), null, null, null, null, null, null, null, null)));
        verifyNoInteractions(sync);
    }

    @Test
    void pendenciaEhRegistradaSemRepetirContador() {
        var r = comItem();
        var item = state.proximo(r);
        state.pendencia(r, item.id, "Conferir vínculo Excel");
        state.pendencia(r, item.id, "Repetição");
        assertEquals(1, atual().pendentes);
        assertEquals("Conferir vínculo Excel", item.mensagem);
        verifyNoInteractions(sync);
    }

    @Test
    void naoFinalizaComFilaOuPaginasAProcessar() {
        var r = comItem();
        assertThrows(ContaAzulAutoState.ReservaPerdida.class, () -> state.devolver(r, true));
        assertNull(atual().concluidoEm);
    }

    @Test
    void rejeitaPaginaCurtaQuePoderiaOcultarDados() {
        var r = state.assumir(7L);
        assertThrows(ResponseStatusException.class, () -> state.pagina(r, new ContaAzulFinancialClient.Pagina(101, List.of(resumo(id)))));
        assertTrue(fila.isEmpty());
    }

    @Test
    void falhaMantemFilaEEsperaProgressivaParaRetomar() {
        var r = comItem();
        var execucao = r.execucaoId();
        var item = state.proximo(r).id;
        state.falhar(r, "Falha temporária", false);
        assertEquals("AGUARDANDO_REPETICAO", atual().status);
        assertEquals(agora.plusSeconds(300), config.proximaEm);
        assertNull(state.assumir(7L));
        agora = config.proximaEm;
        var novo = state.assumir(7L);
        assertEquals(execucao, novo.execucaoId());
        assertEquals(item, state.proximo(novo).id);
    }

    @Test
    void concluiComPendenciasSemDeclararSucessoIntegral() {
        var r = comItem();
        state.pendencia(r, state.proximo(r).id, "Baixa exige revisão");
        state.pagina(r, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.devolver(r, true);
        assertEquals("CONCLUIDA_COM_PENDENCIAS", atual().status);
        assertEquals(agora.plusSeconds(1800), config.proximaEm);
    }

    @Test
    void detalhesDePendenciaSaoPaginadosEIsoladosPelaEmpresa() {
        var r = comItem();
        state.pendencia(r, state.proximo(r).id, "Conferir");
        var s = state.status(7L, 1);
        assertEquals(1, s.totalPendencias());
        assertEquals(id, s.pendencias().get(0).parcelaId());
        assertThrows(IllegalArgumentException.class, () -> state.status(8L, 1));
        assertThrows(IllegalArgumentException.class, () -> state.status(7L, 0));
    }

    @Test
    void listagemRepetidaNaoDuplicaFilaEExigeNovaVarredura() {
        var r = state.assumir(7L);
        var page = new ArrayList<ContaAzulFinancialClient.Parcela>();
        for (int i = 0; i < 100; i++) {
            page.add(resumo(new UUID(0, i + 1).toString()));
        }
        state.pagina(r, new ContaAzulFinancialClient.Pagina(101, page));
        state.pagina(r, new ContaAzulFinancialClient.Pagina(101, List.of(page.get(0))));
        assertEquals(100, fila.size());
        assertTrue(atual().listagemMudou);
    }

    @Test
    void cicloConcluidoRespeitaIntervaloSemSolicitacaoManual() {
        var primeira = state.assumir(7L);
        state.pagina(primeira, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.pagina(primeira, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.devolver(primeira, true);
        Instant marcada = agora.plusSeconds(config.intervaloMinutos * 60L);
        assertEquals(marcada, config.proximaEm);
        agora = marcada.minusSeconds(1);
        assertNull(state.assumir(7L));
        agora = marcada;
        var segunda = state.assumir(7L);
        assertNotNull(segunda);
        assertNotEquals(primeira.execucaoId(), segunda.execucaoId());
    }

    @Test
    void conclusaoAposFalhaNaoExibeMensagemDeErroAntiga() {
        var r = state.assumir(7L);
        state.falhar(r, "Falha temporária", false);
        agora = config.proximaEm;
        var retomada = state.assumir(7L);
        assertEquals(r.execucaoId(), retomada.execucaoId());
        assertNull(atual().mensagem);
        state.pagina(retomada, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.pagina(retomada, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.devolver(retomada, true);
        assertEquals("CONCLUIDA", atual().status);
        assertNull(atual().mensagem);
    }

    @Test
    void novaInstanciaRetomaFilaSemReaplicarItemJaConcluido() {
        var r = comItem();
        UUID item = state.proximo(r).id;
        when(sync.aplicarAutomatico(eq(cliente), eq(3L), eq(item), any(), eq(false)))
                .thenReturn(new Resultado(item, 7L, id, "SEM_ALTERACAO", 123L, agora));
        state.aplicar(r, item, detalhe());
        state.falhar(r, "Interrupção entre etapas", false);
        agora = config.proximaEm;
        var outraInstancia = new ContaAzulAutoState(configs, runs, itens, clientes, usuarios, tx, sync, contexto, integracao, clock, true);
        var nova = outraInstancia.assumir(7L);
        assertEquals(r.execucaoId(), nova.execucaoId());
        assertNull(outraInstancia.proximo(nova));
        outraInstancia.pagina(nova, new ContaAzulFinancialClient.Pagina(0, List.of()));
        outraInstancia.devolver(nova, true);
        assertEquals(1, atual().mantidas);
        verify(sync, times(1)).aplicarAutomatico(eq(cliente), eq(3L), eq(item), any(), eq(false));
    }

    @Test
    void conclusaoParcialMantemAvisoDeCobertura() {
        var r = state.assumir(7L);
        atual().listagemMudou = true;
        atual().mensagem = "Identidade local exige revisão.";
        state.pagina(r, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.pagina(r, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.devolver(r, true);
        assertEquals("PARCIAL", atual().status);
        assertEquals("Identidade local exige revisão.", atual().mensagem);
    }

    @Test
    void periodoMovelExigeNovaAutorizacaoECalculaDatasNoServidor() {
        var pedido = new Configurar(7L, true, LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 2), 30, false, false, false, true);
        assertThrows(IllegalArgumentException.class, () -> state.configurar(pedido));
        assertNull(config.periodoMovel);
        state.configurar(new Configurar(7L, true, pedido.inicio(), pedido.fim(), 30, false, true, false, true));
        assertEquals(Boolean.TRUE, config.periodoMovel);
        assertEquals(LocalDate.of(2026, 10, 17).minusDays(90), config.inicio);
        assertEquals(LocalDate.of(2026, 10, 17).plusDays(90), config.fim);
        assertTrue(state.status(7L, 1).configuracao().periodoMovel());
    }

    @Test
    void periodoMovelNaoDispensaRevisaoDoHistoricoParaInclusoes() {
        assertThrows(IllegalArgumentException.class, () -> state.configurar(new Configurar(7L, true, config.inicio, config.fim, 30, true, true, false, true)));
        assertNull(config.periodoMovel);
    }

    @Test
    void retomadaMantemPeriodoENovaVarreduraAcompanhaCalendario() {
        when(clock.withZone(any())).thenAnswer(i -> Clock.fixed(agora, i.getArgument(0)));
        config.periodoMovel = true;
        var original = state.assumir(7L);
        LocalDate inicioOriginal = atual().inicio;
        LocalDate fimOriginal = atual().fim;
        state.falhar(original, "Interrupção", false);
        agora = agora.plusSeconds(2 * 86400L);
        var retomada = state.assumir(7L);
        assertEquals(original.execucaoId(), retomada.execucaoId());
        assertEquals(inicioOriginal, atual().inicio);
        assertEquals(fimOriginal, atual().fim);
        state.pagina(retomada, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.pagina(retomada, new ContaAzulFinancialClient.Pagina(0, List.of()));
        state.devolver(retomada, true);
        agora = config.proximaEm;
        var nova = state.assumir(7L);
        assertNotEquals(original.execucaoId(), nova.execucaoId());
        assertEquals(inicioOriginal.plusDays(2), atual().inicio);
        assertEquals(fimOriginal.plusDays(2), atual().fim);
    }

    @Test
    void periodoMovelUsaDataDeBrasiliaNaViradaDoAno() {
        agora = Instant.parse("2027-01-01T01:00:00Z");
        when(clock.withZone(any())).thenAnswer(i -> Clock.fixed(agora, i.getArgument(0)));
        config.periodoMovel = true;
        state.assumir(7L);
        assertEquals(LocalDate.of(2026, 12, 31).minusDays(90), atual().inicio);
        assertEquals(LocalDate.of(2026, 12, 31).plusDays(90), atual().fim);
    }

    @Test
    void contratosAntigosContinuamComPeriodoFixo() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        String payload = json.writeValueAsString(Map.of("clienteId", 7, "habilitada", true, "inicio", "2026-10-01", "fim", "2026-10-31", "intervaloMinutos", 30, "incluirNovas", false, "confirmado", true, "confirmoHistorico", false));
        var pedido = json.readValue(payload, Configurar.class);
        assertFalse(pedido.periodoMovel());
        state.configurar(pedido);
        assertEquals(Boolean.FALSE, config.periodoMovel);
        assertEquals(LocalDate.of(2026, 10, 1), config.inicio);
    }

    @Test
    void abrePendenciaAntigaMesmoComNovaExecucaoSemPendencias() {
        var reserva = comItem();
        state.pendencia(reserva, state.proximo(reserva).id, "Conferência anterior");
        var antiga = execucoes.get(reserva.execucaoId());
        var nova = new ContaAzulAutoRun();
        nova.id = UUID.randomUUID();
        nova.clienteId = 7L;
        nova.criadoEm = agora.plusSeconds(1800);
        nova.status = "EM_ANDAMENTO";
        execucoes.put(nova.id, nova);
        var r = state.revisar(7L, antiga.id, 1);
        assertEquals(1, r.total());
        assertEquals(id, r.pendencias().get(0).parcelaId());
        assertEquals(0, state.status(7L, 1).totalPendencias());
        assertThrows(IllegalArgumentException.class, () -> state.revisar(8L, antiga.id, 1));
        assertThrows(ResponseStatusException.class, () -> state.revisar(7L, UUID.randomUUID(), 1));
        assertThrows(IllegalArgumentException.class, () -> state.revisar(7L, antiga.id, 0));
    }


    @Test
    void modoCompletoNaoUsaPeriodoDoDashboardNemJanelaMovel() {
        state.configurar(new Configurar(7L,true,LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),30,true,true,true,true,true));
        assertEquals(Boolean.TRUE,config.sincronizacaoCompleta);
        assertEquals(Boolean.FALSE,config.periodoMovel);
        assertEquals(ContaAzulFullScope.INICIO,config.inicio);
        assertEquals(ContaAzulFullScope.FIM,config.fim);
        assertTrue(state.status(7L,1).configuracao().sincronizacaoCompleta());
    }

    @Test
    void modoCompletoReconsultaVinculoAntigoMesmoSemVencimento() {
        config.sincronizacaoCompleta=true;
        var t=new FinancialTransaction();t.setOrigem("CONTA_AZUL_API");t.setDocumentoReferencia(id);t.setTipo("RECEITA");
        when(tx.findByClienteIdAndOrigemOrderByIdAsc(7L,"CONTA_AZUL_API")).thenReturn(List.of(t));
        var reserva=state.assumir(7L);
        assertTrue(state.cursor(reserva).completa());
        assertEquals(ContaAzulFullScope.INICIO,state.cursor(reserva).inicio());
        assertEquals(id,state.proximo(reserva).parcelaId);
    }

    @Test
    void modoCompletoExigeTransferenciasESaldosAntesDeConcluir() throws Exception {
        var archive=mock(ContaAzulSourceCaptureRepository.class);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        ReflectionTestUtils.setField(state,"fontes",archive);ReflectionTestUtils.setField(state,"json",mapper);
        config.sincronizacaoCompleta=true;var reserva=state.assumir(7L);
        // This test isolates stage completion over one window; full-range coverage is tested below.
        execucoes.get(reserva.execucaoId()).inicio=LocalDate.of(2026,9,1);
        execucoes.get(reserva.execucaoId()).fim=LocalDate.of(2026,9,30);
        state.pagina(reserva,new ContaAzulFinancialClient.Pagina(0,List.of()));
        state.pagina(reserva,new ContaAzulFinancialClient.Pagina(0,List.of()));
        assertEquals(2,state.cursor(reserva).etapa());
        assertThrows(ContaAzulAutoState.ReservaPerdida.class,()->state.devolver(reserva,true));
        var body=mapper.readTree("{\"itens_totais\":0,\"itens\":[]}");
        state.paginaMovimentos(reserva,new ContaAzulMovementClient.Pagina(0,List.of(),body));
        assertEquals(3,state.cursor(reserva).etapa());
        state.paginaMovimentos(reserva,new ContaAzulMovementClient.Pagina(0,List.of(),body));
        assertEquals(4,state.cursor(reserva).etapa());
        state.devolver(reserva,true);
        assertEquals("CONCLUIDA",execucoes.get(reserva.execucaoId()).status);
        verify(archive,times(2)).saveAndFlush(any());
    }

    @Test
    void capturaRepetidaTemMesmaIdentidadeEClienteDaReserva() throws Exception {
        var archive=mock(ContaAzulSourceCaptureRepository.class);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        ReflectionTestUtils.setField(state,"fontes",archive);ReflectionTestUtils.setField(state,"json",mapper);
        config.sincronizacaoCompleta=true;var reserva=state.assumir(7L);
        var body=mapper.readTree("{\"id\":\"parcela\",\"campo_extra\":\"preservado\"}");
        state.arquivar(reserva,"PARCELA",id,body);state.arquivar(reserva,"PARCELA",id,body);
        var captor=org.mockito.ArgumentCaptor.forClass(ContaAzulSourceCapture.class);verify(archive,times(2)).saveAndFlush(captor.capture());
        assertEquals(captor.getAllValues().get(0).id,captor.getAllValues().get(1).id);
        assertEquals(7L,captor.getValue().clienteId);assertTrue(captor.getValue().conteudo.contains("campo_extra"));
        config.habilitada=false;
        assertThrows(ContaAzulAutoState.ReservaPerdida.class,()->state.arquivar(reserva,"PARCELA",id,body));
        assertThrows(IllegalArgumentException.class,()->state.fontes(8L,reserva.execucaoId(),"PARCELA",1));
    }

    private record MovimentoFixture(ContaAzulAutoState.Reserva reserva,ContaAzulAutoRun run,ContaAzulSourceCaptureRepository archive,com.fasterxml.jackson.databind.ObjectMapper mapper) {}
    private MovimentoFixture movimentos(LocalDate inicio,LocalDate fim) {
        agora=Instant.parse("2026-11-02T12:00:00Z");
        when(clock.withZone(any())).thenAnswer(i -> Clock.fixed(agora, i.getArgument(0)));
        var archive=mock(ContaAzulSourceCaptureRepository.class);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        ReflectionTestUtils.setField(state,"fontes",archive);ReflectionTestUtils.setField(state,"json",mapper);
        config.sincronizacaoCompleta=true;var reserva=state.assumir(7L);var r=execucoes.get(reserva.execucaoId());
        r.inicio=inicio;r.fim=fim;r.etapa=2;
        return new MovimentoFixture(reserva,r,archive,mapper);
    }
    private ContaAzulMovementClient.Pagina paginaMov(MovimentoFixture f,int total,int quantidade) {
        var rows=java.util.stream.IntStream.range(0,quantidade).mapToObj(n->(com.fasterxml.jackson.databind.JsonNode)f.mapper().createObjectNode().put("id","tr-"+n)).toList();
        var raw=f.mapper().createObjectNode().put("itens_totais",total);var array=f.mapper().createArrayNode();rows.forEach(array::add);raw.set("itens",array);
        return new ContaAzulMovementClient.Pagina(total,rows,raw);
    }
    @Test void janelaInicialPreservaAlcanceCompletoAutorizado() {
        var f=movimentos(ContaAzulFullScope.INICIO,ContaAzulFullScope.FIM);var cursor=state.cursor(f.reserva());
        assertEquals(ContaAzulFullScope.INICIO,cursor.inicio());assertEquals(ContaAzulFullScope.INICIO.plusDays(29),cursor.fim());
        assertEquals(ContaAzulFullScope.INICIO,f.run().inicio);assertEquals(ContaAzulFullScope.FIM,f.run().fim);
    }
    @Test void paginaDentroDaJanelaAvancaAntesDeMudarPeriodo() {
        var f=movimentos(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));
        state.paginaMovimentos(f.reserva(),paginaMov(f,150,100));
        var c=state.cursor(f.reserva());assertEquals(2,c.pagina());assertEquals(LocalDate.of(2026,9,1),c.inicio());assertEquals(LocalDate.of(2026,9,30),c.fim());
        state.paginaMovimentos(f.reserva(),paginaMov(f,150,50));
        c=state.cursor(f.reserva());assertEquals(1,c.pagina());assertEquals(LocalDate.of(2026,10,1),c.inicio());assertEquals(LocalDate.of(2026,10,30),c.fim());assertEquals(2,c.etapa());assertNull(f.run().totalEsperado);
    }
    @Test void naoConcluiFonteAoEncontrarJanelaVaziaEUsaIdentidadesDistintas() {
        var f=movimentos(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));
        state.paginaMovimentos(f.reserva(),paginaMov(f,0,0));assertEquals(2,state.cursor(f.reserva()).etapa());
        state.paginaMovimentos(f.reserva(),paginaMov(f,0,0));assertEquals(LocalDate.of(2026,10,31),state.cursor(f.reserva()).inicio());assertEquals(LocalDate.of(2026,10,31),state.cursor(f.reserva()).fim());
        assertThrows(ContaAzulAutoState.ReservaPerdida.class,()->state.devolver(f.reserva(),true));
        state.paginaMovimentos(f.reserva(),paginaMov(f,0,0));
        assertEquals(3,state.cursor(f.reserva()).etapa());assertEquals(LocalDate.of(2026,9,1),state.cursor(f.reserva()).inicio());
        var captor=org.mockito.ArgumentCaptor.forClass(ContaAzulSourceCapture.class);verify(f.archive(),times(3)).saveAndFlush(captor.capture());
        assertEquals(3,captor.getAllValues().stream().map(x->x.id).distinct().count());
        assertTrue(captor.getAllValues().stream().allMatch(x->x.chave.startsWith("periodo:")&&x.tipo.equals("TRANSFERENCIA")));
        for(int n=0;n<3;n++)state.paginaMovimentos(f.reserva(),paginaMov(f,0,0));
        assertEquals(4,state.cursor(f.reserva()).etapa());state.devolver(f.reserva(),true);assertEquals("CONCLUIDA",f.run().status);
    }
    @Test void repeticaoMantemCursorContadoresEIdentidadeDaExecucao() {
        var f=movimentos(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));
        f.run().incluidas=238;f.run().atualizadas=2;f.run().mantidas=1531;f.run().pendentes=142;
        state.paginaMovimentos(f.reserva(),paginaMov(f,0,0));
        state.falhar(f.reserva(),"Falha simulada",false);agora=agora.plusSeconds(6*60);
        var nova=state.assumir(7L);assertEquals(f.reserva().execucaoId(),nova.execucaoId());
        assertEquals(LocalDate.of(2026,10,1),state.cursor(nova).inicio());assertEquals(238,f.run().incluidas);assertEquals(2,f.run().atualizadas);assertEquals(1531,f.run().mantidas);assertEquals(142,f.run().pendentes);
    }
    @Test void paginaLegadaParcialNaoMisturaSuaContagemComJanelaNova() {
        var f=movimentos(LocalDate.of(2026,9,1),LocalDate.of(2026,10,31));f.run().pagina=2;f.run().totalEsperado=150;
        assertEquals(f.run().fim,state.cursor(f.reserva()).fim());
        state.paginaMovimentos(f.reserva(),paginaMov(f,150,50));
        var captor=org.mockito.ArgumentCaptor.forClass(ContaAzulSourceCapture.class);verify(f.archive()).saveAndFlush(captor.capture());assertEquals("pagina:2",captor.getValue().chave);
        assertEquals(3,state.cursor(f.reserva()).etapa());assertEquals(LocalDate.of(2026,9,30),state.cursor(f.reserva()).fim());
    }
    @Test void planejamentoCompletoCobreTodosOsDiasSemLacunasNemSobreposicao() {
        var r=new ContaAzulAutoRun();r.inicio=ContaAzulFullScope.INICIO;r.fim=ContaAzulFullScope.FIM;r.pagina=1;
        long dias=0;int janelas=0;LocalDate esperado=r.inicio;
        while(true){var janela=ContaAzulAutoState.janelaMovimentos(r);assertEquals(esperado,janela.inicio());long tamanho=java.time.temporal.ChronoUnit.DAYS.between(janela.inicio(),janela.fim())+1;assertTrue(tamanho>=1&&tamanho<=30);dias+=tamanho;assertTrue(++janelas<5000);if(janela.fim().equals(r.fim))break;esperado=janela.fim().plusDays(1);r.movimentoInicio=esperado;}
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(r.inicio,r.fim)+1,dias);
    }
    @Test void planejamentoIncluiDiaBissexto() {
        var r=new ContaAzulAutoRun();r.inicio=LocalDate.of(2024,2,15);r.fim=LocalDate.of(2024,3,20);r.pagina=1;
        assertEquals(LocalDate.of(2024,3,15),ContaAzulAutoState.janelaMovimentos(r).fim());
    }
    @Test void cursorForaDoAlcanceFalhaEmVezDeOmitirDatas() {
        var f=movimentos(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30));f.run().movimentoInicio=LocalDate.of(2026,10,1);
        assertThrows(IllegalStateException.class,()->state.cursor(f.reserva()));verifyNoInteractions(f.archive());
    }

    @Test void migracaoEmAndamentoPulaPara2000PreservandoExecucaoEContadores() {
        var f=movimentos(LocalDate.of(1900,1,1),LocalDate.of(2200,12,31));f.run().movimentoInicio=LocalDate.of(1913,1,23);
        f.run().incluidas=238;f.run().atualizadas=2;f.run().mantidas=1531;f.run().pendentes=142;
        var c=state.cursor(f.reserva());assertEquals(LocalDate.of(2000,1,1),c.inicio());assertEquals(LocalDate.of(2000,1,30),c.fim());
        assertEquals(f.reserva().execucaoId(),f.run().id);assertEquals(LocalDate.of(1900,1,1),f.run().inicio);assertEquals(LocalDate.of(2200,12,31),f.run().fim);
        assertEquals(238,f.run().incluidas);assertEquals(2,f.run().atualizadas);assertEquals(1531,f.run().mantidas);assertEquals(142,f.run().pendentes);
        assertEquals(LocalDate.of(2026,11,2),f.run().movimentosEscopoFim);verifyNoInteractions(f.archive());
    }
    @Test void novasListagensDeParcelasPreservamFuturasESeparamMovimentos() {
        var f=movimentos(ContaAzulFullScope.INICIO,ContaAzulFullScope.FIM);f.run().etapa=1;
        assertEquals(LocalDate.of(2000,1,1),state.cursor(f.reserva()).inicio());assertEquals(LocalDate.of(2200,12,31),state.cursor(f.reserva()).fim());
        assertTrue(LocalDate.of(2029,3,16).isBefore(state.cursor(f.reserva()).fim()));
        f.run().etapa=2;state.cursor(f.reserva());assertEquals(LocalDate.of(2026,11,2),f.run().movimentosEscopoFim);assertEquals(LocalDate.of(2200,12,31),f.run().fim);
    }
    @Test void dataFinalDaColetaFicaCongeladaAposMudancaDeDia() {
        var f=movimentos(ContaAzulFullScope.INICIO,ContaAzulFullScope.FIM);state.cursor(f.reserva());
        var fim=f.run().movimentosEscopoFim;state.devolver(f.reserva(),false);agora=agora.plusSeconds(86400);
        var outra=state.assumir(7L);state.cursor(outra);assertEquals(f.reserva().execucaoId(),outra.execucaoId());assertEquals(fim,f.run().movimentosEscopoFim);
    }
    @Test void janelaAntigaParcialTerminaAntesDaMigracaoSemMisturarPagina() {
        var f=movimentos(LocalDate.of(1900,1,1),LocalDate.of(2200,12,31));f.run().movimentoInicio=LocalDate.of(1913,1,1);f.run().pagina=2;f.run().totalEsperado=105;
        var antiga=state.cursor(f.reserva());assertEquals(LocalDate.of(1913,1,1),antiga.inicio());assertEquals(LocalDate.of(1913,1,30),antiga.fim());assertNull(f.run().movimentosEscopoInicio);
        state.paginaMovimentos(f.reserva(),paginaMov(f,105,5));var nova=state.cursor(f.reserva());assertEquals(LocalDate.of(2000,1,1),nova.inicio());assertEquals(1,nova.pagina());assertNull(f.run().totalEsperado);
        var captor=org.mockito.ArgumentCaptor.forClass(ContaAzulSourceCapture.class);verify(f.archive()).saveAndFlush(captor.capture());assertEquals("periodo:1913-01-01:1913-01-30:pagina:2",captor.getValue().chave);
    }
    @Test void cursorAntigoNoFuturoAvancaParaSaldosSemReiniciarParcelas() {
        var f=movimentos(LocalDate.of(1900,1,1),LocalDate.of(2200,12,31));f.run().movimentoInicio=LocalDate.of(2100,1,1);
        var c=state.cursor(f.reserva());assertEquals(3,c.etapa());assertEquals(LocalDate.of(2000,1,1),c.inicio());verifyNoInteractions(f.archive());
    }
    @Test void ultimaJanelaRecortaNaDataFixaAntesDeIniciarSaldos() {
        var f=movimentos(ContaAzulFullScope.INICIO,ContaAzulFullScope.FIM);f.run().movimentoInicio=LocalDate.of(2026,10,20);
        assertEquals(LocalDate.of(2026,11,2),state.cursor(f.reserva()).fim());
        state.paginaMovimentos(f.reserva(),paginaMov(f,0,0));assertEquals(3,state.cursor(f.reserva()).etapa());assertEquals(LocalDate.of(2000,1,1),state.cursor(f.reserva()).inicio());
        var captor=org.mockito.ArgumentCaptor.forClass(ContaAzulSourceCapture.class);verify(f.archive()).saveAndFlush(captor.capture());assertEquals("periodo:2026-10-20:2026-11-02:pagina:1",captor.getValue().chave);
    }
    @Test void alcanceSoFuturoNaoGeraConsultaDeMovimentoRealizado() {
        var f=movimentos(LocalDate.of(2029,1,1),LocalDate.of(2030,1,1));assertEquals(4,state.cursor(f.reserva()).etapa());verifyNoInteractions(f.archive());
        assertEquals(LocalDate.of(2029,1,1),f.run().inicio);assertEquals(LocalDate.of(2030,1,1),f.run().fim);
    }
    @Test void escopoPersistidoIncompletoFalhaSemReescreverDados() {
        var f=movimentos(ContaAzulFullScope.INICIO,ContaAzulFullScope.FIM);f.run().movimentosEscopoInicio=LocalDate.of(2000,1,1);
        assertThrows(IllegalStateException.class,()->state.cursor(f.reserva()));verifyNoInteractions(f.archive());
    }
    private ContaAzulAutoState.Reserva incremental() {
        config.sincronizacaoCompleta=true;
        config.alteracoesConfirmadasAte=agora.minusSeconds(3600);
        config.historicoRevisadoEm=agora.minusSeconds(86400);
        return state.assumir(7L);
    }
    @Test void incrementalUsaMarcoComSobreposicaoESemReenfileirarTodoCadastro() {
        var res=incremental();var run=execucoes.get(res.execucaoId());var cursor=state.cursor(res);
        assertTrue(run.incremental);assertEquals(agora.minusSeconds(3600+2*86400),cursor.alteracoesDesde());
        assertEquals(agora,cursor.alteracoesAte());
        assertEquals(ContaAzulFullScope.INICIO,cursor.inicio());assertEquals(ContaAzulFullScope.FIM,cursor.fim());
        verify(tx,never()).findByClienteIdAndOrigemOrderByIdAsc(anyLong(),anyString());
    }
    @Test void cargaHistoricaConcluidaEstabelecePrimeiroMarcoMesmoComPendencias() {
        config.sincronizacaoCompleta=true;var res=state.assumir(7L);var run=execucoes.get(res.execucaoId());
        run.etapa=4;run.pendentes=2;agora=agora.plusSeconds(60);state.devolver(res,true);
        assertEquals(run.criadoEm,config.alteracoesConfirmadasAte);assertEquals(agora,config.historicoRevisadoEm);
        assertEquals("CONCLUIDA_COM_PENDENCIAS",run.status);
    }
    @Test void historicoVencidoForcaNovaRevisaoCompleta() {
        config.sincronizacaoCompleta=true;config.alteracoesConfirmadasAte=agora.minusSeconds(3600);
        config.historicoRevisadoEm=agora.minusSeconds(7*86400);
        var res=state.assumir(7L);assertFalse(execucoes.get(res.execucaoId()).incremental);
        assertNull(state.cursor(res).alteracoesDesde());
        verify(tx).findByClienteIdAndOrigemOrderByIdAsc(7L,"CONTA_AZUL_API");
    }
    @Test void falhaNaoAvancaMarcoERetomaMesmoIntervalo() {
        var res=incremental();var cursor=state.cursor(res);var before=config.alteracoesConfirmadasAte;
        state.falhar(res,"Limite de consultas",false);assertEquals(before,config.alteracoesConfirmadasAte);
        agora=agora.plusSeconds(3600);var resumed=state.assumir(7L);
        assertEquals(res.execucaoId(),resumed.execucaoId());assertEquals(cursor.alteracoesDesde(),state.cursor(resumed).alteracoesDesde());
        assertEquals(cursor.alteracoesAte(),state.cursor(resumed).alteracoesAte());
    }
    @Test void listagemParcialNaoAvancaMarco() {
        var res=incremental();var run=execucoes.get(res.execucaoId());var before=config.alteracoesConfirmadasAte;
        run.etapa=4;run.listagemMudou=true;state.devolver(res,true);
        assertEquals(before,config.alteracoesConfirmadasAte);assertEquals("PARCIAL",run.status);
    }
    @Test void soConfirmaMarcoDepoisDeTodasAsFontes() {
        var res=incremental();var run=execucoes.get(res.execucaoId());var before=config.alteracoesConfirmadasAte;
        run.etapa=2;assertThrows(ContaAzulAutoState.ReservaPerdida.class,()->state.devolver(res,true));
        assertEquals(before,config.alteracoesConfirmadasAte);run.etapa=4;state.devolver(res,true);
        assertEquals(run.alteracoesAte,config.alteracoesConfirmadasAte);
        assertEquals(agora.minusSeconds(86400),config.historicoRevisadoEm);
    }
    @Test void incrementalReconsultaPendenciaMaisRecenteSemRepetirJaResolvida() {
        var first=new ContaAzulAutoItem();first.parcelaId=id;first.tipo="RECEITA";first.status="CONFERIR";
        var resolved=new ContaAzulAutoItem();resolved.parcelaId="1986f173-f531-4660-96ae-04b71c879264";resolved.tipo="DESPESA";resolved.status="SEM_ALTERACAO";
        var old=new ContaAzulAutoItem();old.parcelaId=resolved.parcelaId;old.tipo="DESPESA";old.status="CONFERIR";
        when(itens.findTerminalByCliente(7L)).thenReturn(List.of(first,resolved,old));
        var res=incremental();assertEquals(1,fila.size());assertEquals(id,state.proximo(res).parcelaId);
    }
    @Test void legadoEmAndamentoMantemIdEtapaEAlcance() {
        config.sincronizacaoCompleta=true;var res=state.assumir(7L);var run=execucoes.get(res.execucaoId());run.etapa=1;
        state.devolver(res,false);config.alteracoesConfirmadasAte=agora.minusSeconds(3600);config.historicoRevisadoEm=agora;
        var again=state.assumir(7L);assertEquals(res.execucaoId(),again.execucaoId());assertFalse(run.incremental);
        assertEquals(1,state.cursor(again).etapa());assertNull(state.cursor(again).alteracoesDesde());
    }
    @Test void mudarAutorizacaoExigeNovaCargaAntesDeIncremental() {
        incremental();state.configurar(new Configurar(7L,true,config.inicio,config.fim,30,true,true,true,false,true));
        assertNull(config.alteracoesConfirmadasAte);assertNull(config.historicoRevisadoEm);
    }
    @Test void fontesAuxiliaresIncrementaisUsamJanelaRecenteCongelada() throws Exception {
        var f=movimentos(ContaAzulFullScope.INICIO,ContaAzulFullScope.FIM);
        f.run().incremental=true;f.run().alteracoesDesde=Instant.parse("2026-10-29T12:00:00Z");f.run().alteracoesAte=Instant.parse("2026-10-31T12:00:00Z");
        var c=state.cursor(f.reserva());assertEquals(LocalDate.of(2026,10,29),c.inicio());assertEquals(LocalDate.of(2026,10,31),c.fim());
    }
    @Test void marcoCorrompidoNaoExecutaConsultaSemFiltro() {
        var res=incremental();execucoes.get(res.execucaoId()).alteracoesAte=null;
        assertThrows(IllegalStateException.class,()->state.cursor(res));
    }

}
