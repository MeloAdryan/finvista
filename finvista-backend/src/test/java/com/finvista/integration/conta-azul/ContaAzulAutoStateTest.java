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
        var pedido = new Configurar(7L, true, LocalDate.of(2000,1,1), LocalDate.of(2000,1,2), 30, false, false, false, true);
        assertThrows(IllegalArgumentException.class, () -> state.configurar(pedido));
        assertNull(config.periodoMovel);
        state.configurar(new Configurar(7L, true, pedido.inicio(), pedido.fim(), 30, false, true, false, true));
        assertEquals(Boolean.TRUE, config.periodoMovel);
        assertEquals(LocalDate.of(2026,10,17).minusDays(90), config.inicio);
        assertEquals(LocalDate.of(2026,10,17).plusDays(90), config.fim);
        assertTrue(state.status(7L,1).configuracao().periodoMovel());
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
        assertEquals(LocalDate.of(2026,12,31).minusDays(90), atual().inicio);
        assertEquals(LocalDate.of(2026,12,31).plusDays(90), atual().fim);
    }

    @Test
    void contratosAntigosContinuamComPeriodoFixo() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        String payload = json.writeValueAsString(Map.of("clienteId",7,"habilitada",true,"inicio","2026-10-01","fim","2026-10-31","intervaloMinutos",30,"incluirNovas",false,"confirmado",true,"confirmoHistorico",false));
        var pedido = json.readValue(payload, Configurar.class);
        assertFalse(pedido.periodoMovel());
        state.configurar(pedido);
        assertEquals(Boolean.FALSE, config.periodoMovel);
        assertEquals(LocalDate.of(2026,10,1), config.inicio);
    }

}
