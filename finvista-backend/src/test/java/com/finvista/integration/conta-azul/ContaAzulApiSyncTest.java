package com.finvista.integration.contaazul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.ContaAzulApiSyncResponse.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.ClienteContextService;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulApiSyncTest {

    

          private static final String ID = "9986f173-f531-4660-96ae-04b71c879264";
        private final FinancialTransactionRepository tx = mock(FinancialTransactionRepository.class);
        private final FinancialAllocationRepository allocations = mock(FinancialAllocationRepository.class);
        private final ContaAzulApiOperationRepository audit = mock(ContaAzulApiOperationRepository.class);
        private final ClienteRepository clientes = mock(ClienteRepository.class);
        private final ClienteContextService context = mock(ClienteContextService.class);
        private final ContaAzulConnectionService connections = mock(ContaAzulConnectionService.class);
        private final ContaAzulParcelDetailClient source = mock(ContaAzulParcelDetailClient.class);
        private final Clock clock = mock(Clock.class);
        private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        private final List<FinancialTransaction> records = new ArrayList<>();
        private final List<FinancialAllocation> rows = new ArrayList<>();
        private final Map<UUID, ContaAzulApiOperation> operations = new HashMap<>();
        private ContaAzulApiSyncService service;
        private Cliente cliente;

        @BeforeEach
        void setup() {
            cliente = new Cliente("Empresa de teste");
            ReflectionTestUtils.setField(cliente, "id", 7L);
            var user = new Usuario("Admin", "admin@example.test", "unused", "ADMIN");
            ReflectionTestUtils.setField(user, "id", 3L);
            when(context.getClienteAtualId()).thenReturn(7L);
            when(context.getClienteAtual()).thenReturn(cliente);
            when(context.getUsuarioAutenticado()).thenReturn(user);
            when(clientes.bloquearAtivoPorId(7L)).thenReturn(Optional.of(cliente));
            when(connections.acessoParaConsulta(7L)).thenReturn("test-token");
            when(source.consultar("test-token", ID)).thenReturn(parcela("RECEITA", "50"));
            when(tx.findByClienteId(7L)).thenAnswer(i -> List.copyOf(records));
            when(tx.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(i -> {
                FinancialTransaction value = i.getArgument(0);
                if (value.getId() == null) {
                    ReflectionTestUtils.setField(value, "id", 100L + records.size());
                    records.add(value);
                }
                return value;
            });
            when(allocations.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(eq(7L), anyList()))
                    .thenAnswer(i -> List.copyOf(rows));
            doAnswer(i -> {
                Long id = i.getArgument(0);
                rows.removeIf(r -> r.getLancamento().getId().equals(id));
                return null;
            })
                    .when(allocations).deleteByLancamentoIdAndLancamentoClienteId(anyLong(), eq(7L));
            when(allocations.saveAll(anyList())).thenAnswer(i -> {
                List<FinancialAllocation> values = i.getArgument(0);
                rows.addAll(values);
                return values;
            });
            when(audit.findByIdAndClienteId(any(UUID.class), eq(7L))).thenAnswer(i -> Optional.ofNullable(operations.get(i.getArgument(0))));
            when(audit.saveAndFlush(any())).thenAnswer(i -> {
                ContaAzulApiOperation op = i.getArgument(0);
                operations.put(op.getId(), op);
                return op;
            });
            when(clock.instant()).thenReturn(Instant.parse("2026-10-17T12:00:00Z"));
            var properties = new ContaAzulProperties();
            properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
            service = new ContaAzulApiSyncService(connections, source, new ContaAzulApiPlanService(tx, context),
                    tx, allocations, audit, clientes, context, properties, json, clock);
        }

        private ContaAzulParcelDetail parcela(String tipo, String valor) {
            var v = new BigDecimal(valor);
            return new ContaAzulParcelDetail(ID, "Venda de teste", "PENDENTE", LocalDate.parse("2026-10-21"), null,
                    BigDecimal.ZERO, v, new ContaAzulParcelDetail.Composicao(v, v, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                    new ContaAzulParcelDetail.Evento("evento-teste", tipo, LocalDate.parse("2026-09-21"),
                            new ContaAzulParcelDetail.Condicao(1), List.of(new ContaAzulParcelDetail.Rateio("cat", "Categoria de teste", v, v, List.of()))));
        }

        private Resultado aplicar(Previa p) {
            return service.confirmar(ID, new Confirmacao(7L, p.token(), true, true));
        }

        @Test
        void automaticoNaoDependeDaSessaoDoNavegadorERepeteOperacaoSemDuplicar() {
            UUID op = UUID.randomUUID();
            var p = parcela("RECEITA", "50");
            var resultado = service.aplicarAutomatico(cliente, 3L, op, p, true);
            assertEquals("INCLUIR", resultado.acao());
            assertEquals(1, records.size());
            clearInvocations(tx, allocations, audit, context);
            assertEquals(resultado, service.aplicarAutomatico(cliente, 3L, op, p, true));
            verify(tx, never()).saveAndFlush(any());
            verify(allocations, never()).saveAll(any());
            verifyNoInteractions(context);
        }

        @Test
        void automaticoExigePermissaoEspecificaParaNovasInclusoes() {
            assertThrows(ResponseStatusException.class, () -> service.aplicarAutomatico(cliente, 3L, UUID.randomUUID(), parcela("RECEITA", "50"), false));
            semGravacao();
            verifyNoInteractions(context);
        }

        @Test
        void automaticoMantemCorrespondenciaExcelPendente() {
            excelExistente();
            clearInvocations(context);
            assertThrows(ResponseStatusException.class, () -> service.aplicarAutomatico(cliente, 3L, UUID.randomUUID(), parcela("RECEITA", "50"), true));
            semGravacao();
            verifyNoInteractions(context);
        }

        @Test
        void automaticoAtualizaRegistroConhecidoMesmoComInclusoesDesativadas() {
            service.aplicarAutomatico(cliente, 3L, UUID.randomUUID(), parcela("DESPESA", "50"), true);
            var r = service.aplicarAutomatico(cliente, 3L, UUID.randomUUID(), parcela("DESPESA", "60"), false);
            assertEquals("ATUALIZAR", r.acao());
            assertEquals(1, records.size());
            assertEquals(new BigDecimal("60.00"), records.get(0).getValor());
            verifyNoInteractions(context);
            clearInvocations(audit);
            assertEquals("SEM_ALTERACAO", service.aplicarAutomatico(cliente, 3L, UUID.randomUUID(), parcela("DESPESA", "60"), false).acao());
            verify(audit, never()).saveAndFlush(any());
        }

        private FinancialTransaction excelExistente() {
            aplicar(service.preparar(ID, 7L));
            var t = records.get(0);
            t.setOrigem("CONTA_AZUL_EXCEL");
            t.setDocumentoReferencia("referencia-do-excel");
            t.setObservacoes("Metadado preservado");
            operations.clear();
            clearInvocations(tx, allocations, audit, source);
            return t;
        }

        @Test
        void vinculoMantemIdValoresRateiosEMetadadosComAuditoria() {
            var t = excelExistente();
            var antes = List.copyOf(rows);
            var p = service.prepararVinculo(ID, 7L, t.getId());
            assertEquals("VINCULAR_EXCEL", p.acao());
            semGravacao();
            var r = aplicar(p);
            assertEquals("VINCULAR_EXCEL", r.acao());
            assertEquals(t.getId(), r.lancamentoId());
            assertEquals(1, records.size());
            assertEquals(antes, rows);
            assertEquals("Metadado preservado", t.getObservacoes());
            verify(allocations, never()).deleteByLancamentoIdAndLancamentoClienteId(anyLong(), anyLong());
            verify(allocations, never()).saveAll(anyList());
            var aud = service.auditoria(r.operacaoId(), 7L);
            assertEquals("CONTA_AZUL_EXCEL", aud.antes().path("origem").asText());
            assertEquals("referencia-do-excel", aud.antes().path("documentoReferencia").asText());
            assertEquals("CONTA_AZUL_API", aud.depois().path("origem").asText());
            assertEquals(ID, aud.depois().path("documentoReferencia").asText());
            clearInvocations(source, tx, allocations, audit);
            assertEquals(r, aplicar(p));
            verifyNoInteractions(source, tx, allocations);
        }

        @Test
        void aposVinculoAtualizaMesmoRegistroPelaAPI() {
            var t = excelExistente();
            aplicar(service.prepararVinculo(ID, 7L, t.getId()));
            when(source.consultar("test-token", ID)).thenReturn(parcela("RECEITA", "60"));
            var r = aplicar(service.preparar(ID, 7L));
            assertEquals("ATUALIZAR", r.acao());
            assertEquals(t.getId(), r.lancamentoId());
            assertEquals(1, records.size());
            assertEquals(new BigDecimal("60.00"), t.getValor());
            when(audit.findByClienteIdAndAcaoOrderByConcluidoEmAsc(7L, "VINCULAR_EXCEL"))
                    .thenReturn(operations.values().stream().filter(op -> "VINCULAR_EXCEL".equals(op.resultado().acao())).toList());
            var guard = new com.finvista.service.ContaAzulOriginGuard(audit, json);
            var antigo = new FinancialTransaction(LocalDate.parse("2026-09-21"), "Venda de teste", "RECEITA", new BigDecimal("50"), "Categoria", null, "CONTA_AZUL_EXCEL", "referencia-do-excel");
            antigo.setDataVencimento(LocalDate.parse("2026-10-21"));
            assertTrue(guard.proteger(antigo, t, guard.historico(7L)));
        }

        @Test
        void vinculoDesatualizadoNaoAlteraOrigem() {
            var t = excelExistente();
            var p = service.prepararVinculo(ID, 7L, t.getId());
            t.setObservacoes("Mudou desde a prévia");
            assertThrows(ResponseStatusException.class, () -> aplicar(p));
            assertEquals("CONTA_AZUL_EXCEL", t.getOrigem());
            semGravacao();
        }

        @Test
        void vinculoNaoAceitaRegistroForaDaEmpresaOuOrigemManual() {
            var t = excelExistente();
            assertThrows(ResponseStatusException.class, () -> service.prepararVinculo(ID, 7L, 999L));
            t.setOrigem("CSV");
            assertThrows(ResponseStatusException.class, () -> service.prepararVinculo(ID, 7L, t.getId()));
            semGravacao();
        }

        @Test
        void vinculoRejeitaDiferencasFinanceirasEDatas() {
            var t = excelExistente();
            t.setValorOriginal(new BigDecimal("49"));
            assertThrows(ResponseStatusException.class, () -> service.prepararVinculo(ID, 7L, t.getId()));
            t.setValorOriginal(new BigDecimal("50"));
            t.setDataVencimento(LocalDate.parse("2026-10-22"));
            assertThrows(ResponseStatusException.class, () -> service.prepararVinculo(ID, 7L, t.getId()));
            t.setDataVencimento(LocalDate.parse("2026-10-21"));
            t.setValorTotalRealizado(BigDecimal.ONE);
            assertThrows(ResponseStatusException.class, () -> service.prepararVinculo(ID, 7L, t.getId()));
            semGravacao();
        }

        @Test
        void naoVinculaParcelaQueJaTemIdentidadeAPI() {
            var t = excelExistente();
            aplicar(service.prepararVinculo(ID, 7L, t.getId()));
            assertThrows(ResponseStatusException.class, () -> service.prepararVinculo(ID, 7L, t.getId()));
            assertEquals(1, records.size());
        }

        private void semGravacao() {
            verify(tx, never()).saveAndFlush(any());
            verify(allocations, never()).saveAll(anyList());
            verify(audit, never()).saveAndFlush(any());
        }

        @Test
        void previaNaoGravaESeparaCompetenciaDeVencimento() {
            var p = service.preparar(ID, 7L);
            assertEquals("INCLUIR", p.acao());
            assertEquals(new BigDecimal("50.00"), p.depois().valor());
            assertEquals(LocalDate.parse("2026-09-21"), p.depois().competencia());
            assertEquals(LocalDate.parse("2026-10-21"), p.depois().vencimento());
            semGravacao();
        }

        @Test
        void inclusaoGravaLancamentoRateioEVinculoComAuditoria() {
            var p = service.preparar(ID, 7L);
            var r = aplicar(p);
            assertEquals("INCLUIR", r.acao());
            assertEquals(1, records.size());
            var t = records.get(0);
            assertSame(cliente, t.getCliente());
            assertEquals("CONTA_AZUL_API", t.getOrigem());
            assertEquals(ID, t.getDocumentoReferencia());
            assertEquals(LocalDate.parse("2026-09-21"), t.getData());
            assertEquals(new BigDecimal("50.00"), t.getValor());
            assertEquals(new BigDecimal("50.00"), t.getValorAberto());
            assertEquals(1, rows.size());
            assertEquals(new BigDecimal("50.00"), rows.get(0).getValorCategoria());
            assertNull(rows.get(0).getCentro());
            var aud = service.auditoria(r.operacaoId(), 7L);
            assertEquals(3L, aud.usuarioId());
            assertNull(aud.antes());
            assertEquals("2026-09-21", aud.depois().get("data").asText());
        }

        @Test
        void mesmaConfirmacaoRetornaResultadoSemNovaConsultaOuInclusaoMesmoExpirada() {
            var p = service.preparar(ID, 7L);
            var r = aplicar(p);
            clearInvocations(source, tx, allocations, audit);
            when(clock.instant()).thenReturn(Instant.parse("2026-10-18T12:00:00Z"));
            assertEquals(r, aplicar(p));
            verifyNoInteractions(source, tx, allocations);
            verify(audit, never()).saveAndFlush(any());
            assertEquals(1, records.size());
        }

        @Test
        void outraPreviaParaMesmaParcelaNaoDuplicaEIdentificaSemAlteracao() {
            aplicar(service.preparar(ID, 7L));
            var nova = service.preparar(ID, 7L);
            assertEquals("ATUALIZAR", nova.acao());
            assertEquals("SEM_ALTERACAO", aplicar(nova).acao());
            assertEquals(1, records.size());
            assertEquals(2, operations.size());
        }

        @Test
        void atualizaSomenteRegistroJaVinculadoMantendoID() {
            var inicial = aplicar(service.preparar(ID, 7L));
            when(source.consultar("test-token", ID)).thenReturn(parcela("RECEITA", "60"));
            var previa = service.preparar(ID, 7L);
            assertEquals(new BigDecimal("50.00"), previa.antes().valor());
            assertEquals(new BigDecimal("60.00"), previa.depois().valor());
            var r = aplicar(previa);
            assertEquals("ATUALIZAR", r.acao());
            assertEquals(inicial.lancamentoId(), r.lancamentoId());
            assertEquals(1, records.size());
            assertEquals(1, rows.size());
            assertEquals(new BigDecimal("60.00"), rows.get(0).getValorCategoria());
            assertNotNull(service.auditoria(r.operacaoId(), 7L).antes());
        }

        @Test
        void despesaUsaMesmoFluxoComTipoCorreto() {
            when(source.consultar("test-token", ID)).thenReturn(parcela("DESPESA", "75"));
            aplicar(service.preparar(ID, 7L));
            assertEquals("DESPESA", records.get(0).getTipo());
            assertEquals(new BigDecimal("75.00"), records.get(0).getValorOriginal());
        }

        @Test
        void tokenAlteradoNaoGrava() {
            var p = service.preparar(ID, 7L);
            String[] partes = p.token().split("\\.");
            String token = partes[0] + "." + partes[1] + ".AAAA";
            assertThrows(ResponseStatusException.class, () -> service.confirmar(ID, new Confirmacao(7L, token, true, true)));
            semGravacao();
        }

        @Test
        void exigeConfirmacaoExplicitaEDeclaracaoDeNaoDuplicidade() {
            var p = service.preparar(ID, 7L);
            assertThrows(IllegalArgumentException.class, () -> service.confirmar(ID, new Confirmacao(7L, p.token(), false, true)));
            assertThrows(IllegalArgumentException.class, () -> service.confirmar(ID, new Confirmacao(7L, p.token(), true, false)));
            semGravacao();
        }

        @Test
        void previaExpiradaNaoConsultaFonteNemGrava() {
            var p = service.preparar(ID, 7L);
            when(clock.instant()).thenReturn(Instant.parse("2026-10-17T12:10:00Z"));
            clearInvocations(source);
            assertThrows(ResponseStatusException.class, () -> aplicar(p));
            verifyNoInteractions(source);
            semGravacao();
        }

        @Test
        void alteracaoNaFonteInvalidaPrevia() {
            var p = service.preparar(ID, 7L);
            when(source.consultar("test-token", ID)).thenReturn(parcela("RECEITA", "51"));
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> aplicar(p)).getStatusCode());
            semGravacao();
        }

        @Test
        void alteracaoNoBancoEOutraPreviaConcorrenteSaoDetectadas() {
            var p = service.preparar(ID, 7L);
            var segundo = service.preparar(ID, 7L);
            aplicar(p);
            assertThrows(ResponseStatusException.class, () -> aplicar(segundo));
            assertEquals(1, records.size());
            assertEquals(1, operations.size());
        }

        @Test
        void trocaDeClienteOuUsuarioEPerfilSemPermissaoSaoBloqueados() {
            var p = service.preparar(ID, 7L);
            when(context.getClienteAtualId()).thenReturn(8L);
            assertThrows(ResponseStatusException.class, () -> aplicar(p));
            when(context.getClienteAtualId()).thenReturn(7L);
            var outro = mock(Usuario.class);
            when(outro.getId()).thenReturn(4L);
            when(context.getUsuarioAutenticado()).thenReturn(outro);
            assertThrows(ResponseStatusException.class, () -> aplicar(p));
            doThrow(new IllegalStateException("ADMIN obrigatório")).when(context).validarAdministrador();
            assertThrows(IllegalStateException.class, () -> service.preparar(ID, 7L));
            semGravacao();
        }

        @Test
        void possivelCorrespondenciaExcelMesmoComOutroNomeBloqueiaInclusao() {
            var legado = new FinancialTransaction(LocalDate.parse("2026-09-21"), "Outro nome", "RECEITA", new BigDecimal("50"),
                    "Categoria", null, "CONTA_AZUL_EXCEL", "referencia-legada");
            legado.setCliente(cliente);
            ReflectionTestUtils.setField(legado, "id", 9L);
            records.add(legado);
            assertThrows(ResponseStatusException.class, () -> service.preparar(ID, 7L));
            semGravacao();
        }

        @Test
        void duplicidadeDeIdentidadeExternaBloqueiaAtualizacao() {
            aplicar(service.preparar(ID, 7L));
            var duplicado = new FinancialTransaction(LocalDate.parse("2026-09-21"), "Outro", "RECEITA", new BigDecimal("50"),
                    "Categoria", null, "CONTA_AZUL_API", ID);
            duplicado.setCliente(cliente);
            ReflectionTestUtils.setField(duplicado, "id", 999L);
            records.add(duplicado);
            assertThrows(ResponseStatusException.class, () -> service.preparar(ID, 7L));
        }

        @Test
        void falhaDeConsultaNaoCriaLancamento() {
            var p = service.preparar(ID, 7L);
            when(source.consultar("test-token", ID)).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
            assertThrows(ResponseStatusException.class, () -> aplicar(p));
            semGravacao();
        }

        @Test
        void ajustesBaixasEPerdasNaoSaoSincronizados() {
            var base = parcela("RECEITA", "50");
            var ajuste = new ContaAzulParcelDetail(ID, base.descricao(), base.status(), base.vencimento(), base.liquido(),
                    base.pago(), base.pendente(), new ContaAzulParcelDetail.Composicao(new BigDecimal("50"), new BigDecimal("50"),
                    BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), base.evento());
            when(source.consultar("test-token", ID)).thenReturn(ajuste);
            assertThrows(ResponseStatusException.class, () -> service.preparar(ID, 7L));
            var quitado = new ContaAzulParcelDetail(ID, base.descricao(), "QUITADO", base.vencimento(), new BigDecimal("50"),
                    new BigDecimal("50"), BigDecimal.ZERO, base.composicao(), base.evento());
            when(source.consultar("test-token", ID)).thenReturn(quitado);
            assertThrows(ResponseStatusException.class, () -> service.preparar(ID, 7L));
            semGravacao();
        }

        @Test
        void centrosSaoDivididosSemRepetirValorDaCategoria() {
            var p = parcela("RECEITA", "50");
            var centros = List.of(new ContaAzulParcelDetail.Centro("a", "A", new BigDecimal("20"), new BigDecimal("20")),
                    new ContaAzulParcelDetail.Centro("b", "B", new BigDecimal("30"), new BigDecimal("30")));
            var detalhado = new ContaAzulParcelDetail(ID, p.descricao(), p.status(), p.vencimento(), p.liquido(), p.pago(), p.pendente(), p.composicao(),
                    new ContaAzulParcelDetail.Evento("evento", "RECEITA", p.evento().competencia(), p.evento().condicao(),
                            List.of(new ContaAzulParcelDetail.Rateio("cat", "Categoria", new BigDecimal("50"), new BigDecimal("50"), centros))));
            when(source.consultar("test-token", ID)).thenReturn(detalhado);
            aplicar(service.preparar(ID, 7L));
            assertEquals(2, rows.size());
            assertEquals(new BigDecimal("50.00"), rows.stream().map(FinancialAllocation::getValorCategoria).reduce(BigDecimal.ZERO, BigDecimal::add));
            assertEquals(new BigDecimal("20.00"), rows.get(0).getValorCentro());
        }

        @Test
        void pedidosNaoAceitamValoresOuProprietarioForjados() {
            assertThrows(Exception.class, () -> json.readValue("{\"clienteId\":7,\"valor\":1}", Pedido.class));
            assertThrows(Exception.class, () -> json.readValue("{\"clienteId\":7,\"lancamentoId\":1}", Confirmacao.class));
        }
    }

    