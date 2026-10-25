package com.finvista.service;
import com.fasterxml.jackson.databind.ObjectMapper;import com.finvista.model.*;import com.finvista.repository.*;import java.math.BigDecimal;import java.time.*;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class FinancialLedgerServiceTest {
 final FinancialTransactionRepository tx=mock(FinancialTransactionRepository.class);final ContaAzulApiOperationRepository ops=mock(ContaAzulApiOperationRepository.class);final ContaAzulSourceCaptureRepository sources=mock(ContaAzulSourceCaptureRepository.class);final ContaAzulAutoRunRepository runs=mock(ContaAzulAutoRunRepository.class);final ClienteContextService ctx=mock(ClienteContextService.class);
 final ContaAzulAutoRun run=new ContaAzulAutoRun();final List<ContaAzulSourceCapture> data=new ArrayList<>();
 final FinancialLedgerService service=new FinancialLedgerService(tx,ops,sources,runs,ctx,new ObjectMapper().findAndRegisterModules(),Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"),ZoneOffset.UTC));
 @BeforeEach void setup(){when(ctx.getClienteAtualId()).thenReturn(7L);run.id=UUID.randomUUID();run.clienteId=7L;run.status="CONCLUIDA";run.concluidoEm=Instant.parse("2026-10-23T12:00:00Z");when(runs.findFirstByClienteIdAndSincronizacaoCompletaTrueAndStatusInOrderByConcluidoEmDesc(eq(7L),any())).thenReturn(Optional.of(run));when(sources.findByClienteIdAndExecucaoIdOrderByTipoAscChaveAsc(7L,run.id)).thenReturn(data);when(tx.findByClienteId(7L)).thenReturn(List.of());when(ops.snapshotsAtuais(7L)).thenReturn(List.of());}
 void capture(String type,String content){var c=new ContaAzulSourceCapture();c.tipo=type;c.chave="pagina:"+data.size();c.conteudo="{\"itens\":["+content+"]}";data.add(c);}
 String seed(String account,String value,String type){return "{\"tipo\":\""+type+"\",\"id_conta_financeira\":\""+account+"\",\"data_competencia\":\"2026-08-01\",\"saldo_inicial\":"+value+"}";}
 String transfer(String value){return "{\"id\":\"transfer-1\",\"data\":\"2026-09-10\",\"origem\":{\"data\":\"2026-09-10\",\"conta_financeira\":{\"id\":\"A\",\"nome\":\"Banco A\"},\"composicao_valor\":{\"valor_liquido\":"+value+"}},\"destino\":{\"data\":\"2026-09-10\",\"conta_financeira\":{\"id\":\"B\",\"nome\":\"Banco B\"},\"composicao_valor\":{\"valor_liquido\":"+value+"}}}";}
 FinancialLedgerService.Result read(){return service.consultar(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30));}
 @Test void transferAffectsAccountsButNotConsolidatedBalance(){capture("SALDO_INICIAL",seed("A","1000","RECEITA")+","+seed("B","200","RECEITA"));capture("TRANSFERENCIA",transfer("100"));var r=read();assertFalse(r.parcial());assertEquals(0,new BigDecimal("900").compareTo(r.contas().get(0).saldoFinal()));assertEquals(0,new BigDecimal("300").compareTo(r.contas().get(1).saldoFinal()));assertEquals(0,new BigDecimal("1200").compareTo(r.meses().get(0).saldoFinal()));assertEquals(0,r.contas().get(0).pagamentos().signum());}
 @Test void repeatTransferPagesAreIdempotent(){capture("SALDO_INICIAL",seed("A","1000","RECEITA")+","+seed("B","0","RECEITA"));capture("TRANSFERENCIA",transfer("100"));capture("TRANSFERENCIA",transfer("100"));assertEquals(2,read().movimentos().size());}
 @Test void divergentTransferIdsExcludeBothSides(){capture("TRANSFERENCIA",transfer("100"));capture("TRANSFERENCIA",transfer("200"));var r=read();assertTrue(r.parcial());assertEquals(0,r.movimentos().size());}
 @Test void negativeOpeningIsNotOperatingExpense(){capture("SALDO_INICIAL",seed("A","-250","DESPESA"));var r=read();assertEquals(0,new BigDecimal("-250").compareTo(r.contas().get(0).saldoFinal()));assertEquals(0,r.contas().get(0).pagamentos().signum());}
 @Test void missingAccountDoesNotBecomeCertifiedZero(){capture("TRANSFERENCIA",transfer("100"));assertTrue(read().parcial());assertTrue(read().contas().stream().allMatch(FinancialLedgerService.Account::semSaldoInicial));}
 @Test void missingWideRunKeepsCoveragePartial(){when(runs.findFirstByClienteIdAndSincronizacaoCompletaTrueAndStatusInOrderByConcluidoEmDesc(eq(7L),any())).thenReturn(Optional.empty());assertTrue(read().parcial());verifyNoInteractions(sources);}
 @Test void invalidDatesFailBeforeReadingSources(){assertThrows(IllegalArgumentException.class,()->service.consultar(LocalDate.of(2026,10,1),LocalDate.of(2026,9,1)));verifyNoInteractions(tx,ops,sources);}

 @Test void auditedReceiptAndTransfersReconcileWithOpeningBalances() throws Exception {
  capture("SALDO_INICIAL",seed("A","1000","RECEITA")+","+seed("B","200","RECEITA"));capture("TRANSFERENCIA",transfer("100"));
  String raw="""
  {"id":"p1","descricao":"Recebimento","status":"QUITADO","data_vencimento":"2026-12-01","valor_total_liquido":102,"valor_pago":102,"nao_pago":0,
   "valor_composicao":{"valor_bruto":100,"valor_liquido":102,"juros":2,"multa":0,"desconto":0,"taxa":0},
   "evento":{"id":"e1","tipo":"RECEITA","data_competencia":"2026-08-01","condicao_pagamento":{"quantidade_parcelas":1}},
   "baixas":[{"id":"b1","id_parcela":"p1","data_pagamento":"2026-09-10","valor_composicao":{"valor_bruto":100,"valor_liquido":102,"juros":2,"multa":0,"desconto":0,"taxa":0},"conta_financeira":{"id":"A","nome":"Banco A"}}]}
  """;
  var t=new FinancialTransaction();org.springframework.test.util.ReflectionTestUtils.setField(t,"id",1L);t.setOrigem("CONTA_AZUL_API");t.setDocumentoReferencia("p1");t.setTipo("RECEITA");t.setSituacao("QUITADO");t.setDataCompetencia(LocalDate.of(2026,8,1));t.setValor(new BigDecimal("100"));t.setValorRealizado(new BigDecimal("100"));t.setValorTotalRealizado(new BigDecimal("102"));t.setValorTotalAberto(BigDecimal.ZERO);
  var op=new ContaAzulApiOperation(UUID.randomUUID(),new Cliente("Teste"),3L,"p1","INCLUIR",1L,Instant.now(),null,"{}",raw);when(ops.snapshotsAtuais(7L)).thenReturn(List.of(op));when(tx.findByClienteId(7L)).thenReturn(List.of(t));
  var c=new ContaAzulSourceCapture();c.tipo="PARCELA";c.chave="p1";c.conteudo=raw;data.add(c);
  var r=read();assertFalse(r.parcial());assertEquals(0,new BigDecimal("1302").compareTo(r.meses().get(0).saldoFinal()));assertEquals(0,new BigDecimal("102").compareTo(r.contas().get(0).recebimentos()));assertEquals(0,r.contas().get(0).pagamentos().signum());
 }

 @Test void openingAfterRequestedPeriodMustNotEraseEarlierTransfers(){capture("SALDO_INICIAL",seed("A","1000","RECEITA").replace("2026-08-01","2026-10-01"));capture("TRANSFERENCIA",transfer("100"));var r=read();assertTrue(r.parcial());assertEquals(2,r.movimentos().size());assertEquals(0,new BigDecimal("-100").compareTo(r.contas().get(0).saldoFinal()));}
 @Test void movementsBeforeBaselineAreNotAppliedTwiceAndRemainFlagged(){capture("SALDO_INICIAL",seed("A","1000","RECEITA").replace("2026-08-01","2026-09-20"));capture("TRANSFERENCIA",transfer("100"));var r=read();assertTrue(r.avisos().stream().anyMatch(x->x.contains("anteriores à abertura")));assertEquals(0,new BigDecimal("1000").compareTo(r.contas().get(0).saldoFinal()));}
}
