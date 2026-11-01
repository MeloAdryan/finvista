package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaAzulReconciliationServiceTest {

    final ClienteContextService ctx = mock(ClienteContextService.class);
    final FinancialViewService view = mock(FinancialViewService.class);
    final LocalDate inicio = LocalDate.of(2026, 9, 1), fim = LocalDate.of(2026, 9, 30);

    FinancialViewService.Resposta response(String receipt, String expense, boolean empty) {
        var r = mock(FinancialViewService.Resposta.class);
        when(r.clienteId()).thenReturn(7L);
        when(r.totais()).thenReturn(new FinancialViewService.Total(new BigDecimal(receipt), new BigDecimal(expense), new BigDecimal(receipt).subtract(new BigDecimal(expense))));
        when(r.linhas()).thenReturn(empty ? List.of() : List.of(new FinancialViewService.Linha(1L, "x", "Item", "DESPESA", inicio, inicio, inicio, "C", "Centro", BigDecimal.ONE)));
        when(r.exclusoes()).thenReturn(Map.of());
        return r;
    }

    void setup(String apiR, String apiD, String fileR, String fileD, boolean empty) {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(view.consultar(any())).thenAnswer(i -> ((FinancialViewService.Filtro) i.getArgument(0)).criterio() == FinancialViewService.Criterio.BAIXA ? response(apiR, apiD, false) : response(fileR, fileD, empty));
    }

    @Test
    void detectaDivergenciaRecebidaMesmoComDespesasIguais() {
        setup("120.00", "113958.02", "100.00", "113958.02", false);
        var r = new ContaAzulReconciliationService(ctx, view).conferir(7L, inicio, fim);
        assertEquals("DIVERGENCIA", r.situacao());
        assertEquals(new BigDecimal("20.00"), r.diferencaReceita());
        assertEquals(0, r.diferencaDespesa().signum());
        verify(view, times(2)).consultar(any());
    }

    @Test
    void totaisIguaisNaoCertificamVinculoNemCobertura() {
        setup("100", "200", "100", "200", false);
        assertEquals("TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS", new ContaAzulReconciliationService(ctx, view).conferir(7L, inicio, fim).situacao());
    }

    @Test
    void extratoAusenteNaoEquivaleAConferenciaZerada() {
        setup("100", "200", "0", "0", true);
        assertEquals("SEM_DADOS_COMPARAVEIS", new ContaAzulReconciliationService(ctx, view).conferir(7L, inicio, fim).situacao());
    }

    @Test
    void nuncaConsultaOutraEmpresa() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        assertThrows(IllegalArgumentException.class, () -> new ContaAzulReconciliationService(ctx, view).conferir(8L, inicio, fim));
        verifyNoInteractions(view);
    }

    private FinancialViewService.Linha line(long id, String doc, String payment, String value, String account, LocalDate date) {
        return new FinancialViewService.Linha(id, doc, "Pagamento", "RECEITA", date, date, date, "Categoria", "Centro", new BigDecimal(value), null, null, null, null, null, payment, account);
    }

    private FinancialViewService.Resposta detailed(List<FinancialViewService.Linha> lines, String receipt) {
        var r = mock(FinancialViewService.Resposta.class);
        when(r.clienteId()).thenReturn(7L);
        when(r.linhas()).thenReturn(lines);
        when(r.exclusoes()).thenReturn(Map.of());
        when(r.totais()).thenReturn(new FinancialViewService.Total(new BigDecimal(receipt), BigDecimal.ZERO, new BigDecimal(receipt)));
        return r;
    }

    private ContaAzulReconciliationService withFields(Map<String, String> fields) {
        var service = new ContaAzulReconciliationService(ctx, view);
        var repo = mock(com.finvista.repository.StatementMovementRepository.class);
        var importer = mock(StatementImportService.class);
        var row = mock(com.finvista.model.StatementMovement.class);
        when(row.getChave()).thenReturn("h");
        when(row.getOcorrencia()).thenReturn(1);
        var decoded = mock(ContaAzulStatementService.Line.class);
        when(decoded.campos()).thenReturn(fields);
        when(importer.decode(row)).thenReturn(decoded);
        when(repo.findByClienteIdOrderByIdAsc(7L)).thenReturn(List.of(row));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "statements", repo);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "statementImport", importer);
        when(ctx.getClienteAtualId()).thenReturn(7L);
        return service;
    }

    @Test
    void rateiosEAjustesNaoDuplicamMovimentoEContaEntraNaConferencia() {
        var a = detailed(List.of(line(1, "p", "b", "100", "conta", inicio), line(1, "p", "b", "-3", "conta", inicio)), "97");
        var f = detailed(List.of(line(2, "EXTRATO:h:1", null, "97", null, inicio)), "97");
        when(view.consultar(any())).thenAnswer(i -> ((FinancialViewService.Filtro) i.getArgument(0)).criterio() == FinancialViewService.Criterio.BAIXA ? a : f);
        var service = withFields(Map.of("ID DA BAIXA", "B", "id_parcela", "p", "ID_CONTA_FINANCEIRA", "CONTA"));
        var r = service.conferir(7L, inicio, fim);
        assertEquals(1, r.resumo().movimentosApi());
        assertEquals(1, r.resumo().porId());
        assertTrue(r.resumo().totaisReproduzidos());
        assertEquals(0, r.resumo().contasNaoComparaveis());
        assertEquals("VALORES_POR_ID_CONFERIDOS_COBERTURA_NAO_CERTIFICADA", r.situacao());
        assertFalse(r.cobertura().coberturaConfirmada());
        assertFalse(r.cobertura().consultaAoVivo());
    }

    @Test
    void totaisIguaisComIdsDiferentesContinuamDivergenciaDeRegistros() {
        var a = detailed(List.of(line(1, "p", "b", "100", null, inicio)), "100");
        var f = detailed(List.of(line(2, "EXTRATO:h:1", null, "100", null, inicio)), "100");
        when(view.consultar(any())).thenAnswer(i -> ((FinancialViewService.Filtro) i.getArgument(0)).criterio() == FinancialViewService.Criterio.BAIXA ? a : f);
        var r = withFields(Map.of("id da baixa", "outro")).conferir(7L, inicio, fim);
        assertEquals("DIVERGENCIA_DE_REGISTROS", r.situacao());
        assertEquals(0, r.diferencaReceita().signum());
        assertTrue(r.detalhes().correspondencias().isEmpty());
    }

    @Test
    void somaIncompativelDosDetalhesNaoEhApresentadaComoVerificada() {
        setup("100", "200", "100", "200", false);
        assertFalse(new ContaAzulReconciliationService(ctx, view).conferir(7L, inicio, fim).resumo().totaisReproduzidos());
    }

    @Test
    void novaExecucaoSemPendenciasNaoApagaPendenciaAnteriorDaEmpresa() {
        setup("100", "200", "100", "200", false);
        var service = new ContaAzulReconciliationService(ctx, view);
        var items = mock(com.finvista.repository.ContaAzulAutoItemRepository.class);
        var runs = mock(com.finvista.repository.ContaAzulAutoRunRepository.class);
        var resolvida = new com.finvista.model.ContaAzulAutoItem();
        resolvida.parcelaId = "p1";
        resolvida.status = "ATUALIZAR";
        var antiga = new com.finvista.model.ContaAzulAutoItem();
        antiga.parcelaId = "p1";
        antiga.status = "CONFERIR";
        var pendente = new com.finvista.model.ContaAzulAutoItem();
        pendente.parcelaId = "p2";
        pendente.status = "CONFERIR";
        when(items.findTerminalByCliente(7L)).thenReturn(List.of(resolvida, pendente, antiga));
        var latest = new com.finvista.model.ContaAzulAutoRun();
        latest.status = "CONCLUIDA";
        latest.pendentes = 0;
        when(runs.findTop10ByClienteIdOrderByCriadoEmDesc(7L)).thenReturn(List.of(latest));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "autoItems", items);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "autoRuns", runs);
        assertEquals(1, service.conferir(7L, inicio, fim).cobertura().pendenciasEmpresa());
    }

    @Test
    void aliasesDeIdConflitantesNoMesmoExtratoNaoSaoEscolhidosArbitrariamente() {
        var a = detailed(List.of(line(1, "p", "b", "100", null, inicio)), "100");
        var f = detailed(List.of(line(2, "EXTRATO:h:1", null, "100", null, inicio)), "100");
        when(view.consultar(any())).thenAnswer(i -> ((FinancialViewService.Filtro) i.getArgument(0)).criterio() == FinancialViewService.Criterio.BAIXA ? a : f);
        assertThrows(IllegalArgumentException.class, () -> withFields(Map.of("id da baixa", "b", "ID_BAIXA", "outro")).conferir(7L, inicio, fim));
    }

    @Test
    void fontesSaoLidasNaMesmaTransacaoComSnapshotConsistente() throws Exception {
        for (String name : List.of("conferir", "conferirLeitura")) {
            var annotation = ContaAzulReconciliationService.class.getMethod(name, Long.class, LocalDate.class, LocalDate.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertTrue(annotation.readOnly());
            assertEquals(org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, annotation.isolation());
        }
    }

    class Links {
        final com.fasterxml.jackson.databind.ObjectMapper mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        final com.finvista.repository.StatementMovementRepository repo=mock(com.finvista.repository.StatementMovementRepository.class);
        final StatementImportService importer=mock(StatementImportService.class);
        final ContaAzulReconciliationService service=new ContaAzulReconciliationService(ctx,view);
        final com.finvista.model.Cliente company=new com.finvista.model.Cliente("Teste");
        final com.finvista.model.StatementMovement row;
        final java.util.List<com.finvista.model.StatementMovement> saved=new java.util.ArrayList<>();
        final String fileKey="2:EXTRATO:h:1:null:2026-09-01:RECEITA";
        final String apiKey="1:p:b:2026-09-01:RECEITA";
        FinancialViewService.Resposta currentApi=detailed(List.of(line(1,"p","b","100","conta-1",inicio)),"100");
        Links()throws Exception{
            var original=new ContaAzulStatementService.Line(2,inicio,inicio,inicio,"Pagamento","Receita","Lançamento Financeiro","Banco Teste","Pago",new BigDecimal("100"),List.of(),List.of(),List.of(),List.of(),Map.of("conta bancaria","Banco Teste"));
            row=new com.finvista.model.StatementMovement(company,"h",1,mapper.writeValueAsString(original),"teste.xls",java.time.Instant.EPOCH);
            org.springframework.test.util.ReflectionTestUtils.setField(row,"id",2L);org.springframework.test.util.ReflectionTestUtils.setField(company,"id",7L);saved.add(row);
            when(repo.findByClienteIdOrderByIdAsc(7L)).thenAnswer(i->List.copyOf(saved));
            when(importer.decode(any())).thenAnswer(i->mapper.readValue(((com.finvista.model.StatementMovement)i.getArgument(0)).getConteudo(),ContaAzulStatementService.Line.class));
            when(ctx.getClienteAtualId()).thenReturn(7L);when(ctx.getClienteAtual()).thenReturn(company);
            var user=new com.finvista.model.Usuario("Contador Teste","contador@example.test","hash","ADMIN");org.springframework.test.util.ReflectionTestUtils.setField(user,"id",9L);when(ctx.getUsuarioAutenticado()).thenReturn(user);
            when(view.consultar(any())).thenAnswer(i->((FinancialViewService.Filtro)i.getArgument(0)).criterio()==FinancialViewService.Criterio.BAIXA?currentApi:detailed(List.of(line(2,"EXTRATO:h:1",null,"100",null,inicio)),"100"));
            org.springframework.test.util.ReflectionTestUtils.setField(service,"statements",repo);org.springframework.test.util.ReflectionTestUtils.setField(service,"statementImport",importer);
            org.springframework.test.util.ReflectionTestUtils.setField(service,"json",mapper);org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",java.time.Clock.fixed(java.time.Instant.parse("2026-10-31T12:00:00Z"),java.time.ZoneOffset.UTC));
        }
        ContaAzulReconciliationService.LinkRequest request(String action){return new ContaAzulReconciliationService.LinkRequest(7L,inicio,fim,fileKey,apiKey,action,"Recibo original e conta bancária conferidos.");}
    }
    @Test void previaNaoGravaENomeDeContaNaoCriaId()throws Exception{
        var f=new Links();var before=f.row.getConteudo();var r=f.service.conferir(7L,inicio,fim);
        assertNull(r.detalhes().correspondencias().get(0).extrato().contaId());assertEquals(2,r.contas().size());
        f.service.planejarVinculo(f.request("VINCULAR"));assertEquals(before,f.row.getConteudo());verify(f.repo,never()).saveAndFlush(any());
    }
    @Test void confirmaUmParaUmSemAlterarValoresOuCamposOriginais()throws Exception{
        var f=new Links();var before=f.importer.decode(f.row);f.service.confirmarVinculo(f.service.planejarVinculo(f.request("VINCULAR")));
        var r=f.service.conferir(7L,inicio,fim);assertEquals(1,r.resumo().porConferencia());assertEquals(0,r.resumo().porId());assertEquals(0,r.resumo().contasNaoComparaveis());assertEquals(1,r.contas().size());
        assertEquals(new BigDecimal("100"),r.contas().get(0).receitaExtrato());assertEquals(r.contas().get(0).receitaApi(),r.contas().get(0).receitaExtrato());
        var after=f.importer.decode(f.row);assertEquals(before.movimento(),after.movimento());assertEquals(before.valor(),after.valor());assertEquals(before.conta(),after.conta());
        assertFalse(after.campos().containsKey("id da baixa"));assertEquals("b",after.campos().get("finvista:conciliacao baixa"));assertTrue(after.campos().get("finvista:conciliacao historico").contains("Contador Teste"));
    }
    @Test void alteracaoNaApiInvalidaConferenciaSemEsconderDiferenca()throws Exception{
        var f=new Links();f.service.confirmarVinculo(f.service.planejarVinculo(f.request("VINCULAR")));
        f.currentApi=detailed(List.of(line(1,"p","b","110","conta-1",inicio)),"110");var r=f.service.conferir(7L,inicio,fim);
        assertEquals(0,r.resumo().porConferencia());assertFalse(r.detalhes().divergencias().isEmpty());assertEquals(new BigDecimal("10"),r.diferencaReceita());assertFalse(r.detalhes().divergencias().get(0).extrato().revisao().atual());
    }
    @Test void mudancaEntrePreviaEConfirmacaoNaoGrava()throws Exception{
        var f=new Links();var plan=f.service.planejarVinculo(f.request("VINCULAR"));f.currentApi=detailed(List.of(line(1,"p","b","110","conta-1",inicio)),"110");
        assertThrows(IllegalArgumentException.class,()->f.service.confirmarVinculo(plan));verify(f.repo,never()).saveAndFlush(any());
    }
    @Test void mesmaBaixaNaoPodeSerVinculadaADoisMovimentos()throws Exception{
        var f=new Links();var original=f.importer.decode(f.row);var fields=new java.util.LinkedHashMap<>(original.campos());fields.put("id da baixa","b");
        var other=new ContaAzulStatementService.Line(3,inicio,inicio,inicio,"Outro","Receita","Lançamento Financeiro","Banco","Pago",new BigDecimal("100"),List.of(),List.of(),List.of(),List.of(),fields);
        var row=new com.finvista.model.StatementMovement(f.company,"outro",1,f.mapper.writeValueAsString(other),"teste.xls",java.time.Instant.EPOCH);org.springframework.test.util.ReflectionTestUtils.setField(row,"id",3L);f.saved.add(row);
        assertThrows(IllegalArgumentException.class,()->f.service.planejarVinculo(f.request("VINCULAR")));verify(f.repo,never()).saveAndFlush(any());
    }
    @Test void desfazerPreservaHistoricoEDoisTotaisIndependentes()throws Exception{
        var f=new Links();f.service.confirmarVinculo(f.service.planejarVinculo(f.request("VINCULAR")));f.service.confirmarVinculo(f.service.planejarVinculo(f.request("DESVINCULAR")));
        var r=f.service.conferir(7L,inicio,fim);assertEquals(0,r.resumo().porConferencia());assertEquals(1,r.resumo().sugestoes());assertEquals(new BigDecimal("100"),r.extrato().receita());assertEquals(r.api().receita(),r.extrato().receita());
        assertTrue(f.importer.decode(f.row).campos().get("finvista:conciliacao historico").contains("DESVINCULAR"));
    }
    @Test void perfilSemPermissaoNaoPreparaVinculo()throws Exception{
        var f=new Links();doThrow(new IllegalStateException("Apenas administradores")).when(ctx).validarAdministrador();assertThrows(IllegalStateException.class,()->f.service.planejarVinculo(f.request("VINCULAR")));verify(f.repo,never()).saveAndFlush(any());
    }
    @Test void outraEmpresaNaoReaproveitaConferencia()throws Exception{
        var f=new Links();var p=f.service.planejarVinculo(f.request("VINCULAR"));when(ctx.getClienteAtualId()).thenReturn(8L);assertThrows(IllegalArgumentException.class,()->f.service.confirmarVinculo(p));verify(f.repo,never()).saveAndFlush(any());
    }
    @Test void semContaNaApiNaoInventaIdentificacao()throws Exception{
        var f=new Links();f.currentApi=detailed(List.of(line(1,"p","b","100",null,inicio)),"100");assertThrows(IllegalArgumentException.class,()->f.service.planejarVinculo(f.request("VINCULAR")));verify(f.repo,never()).saveAndFlush(any());
    }

}
