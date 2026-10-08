package com.finvista.service;
import com.finvista.model.*;
import com.finvista.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpenseExplorerServiceTest {
 private final FinancialTransactionRepository transactions=mock(FinancialTransactionRepository.class);
 private final FinancialAllocationRepository rates=mock(FinancialAllocationRepository.class);
 private final CostCategoryClassificationRepository classifications=mock(CostCategoryClassificationRepository.class);
 private final ClienteContextService context=mock(ClienteContextService.class);
 private final FinancialReferenceService reference=mock(FinancialReferenceService.class);
 private final ExpenseExplorerService service=new ExpenseExplorerService(transactions,classifications,context,reference,new ExpenseAllocationService(rates));
 private final LocalDate de=LocalDate.of(2026,10,1),ate=LocalDate.of(2026,10,31);
 private FinancialTransaction t(long id,String data,String tipo,String valor,String categoria,String centro) {
  var t=mock(FinancialTransaction.class);when(t.getId()).thenReturn(id);when(t.getData()).thenReturn(LocalDate.parse(data));when(t.getTipo()).thenReturn(tipo);when(t.getValor()).thenReturn(new BigDecimal(valor));when(t.getCategoria()).thenReturn(categoria);when(t.getCentroCusto()).thenReturn(centro);return t;
 }
 private void dados(boolean negativo) {
  when(context.getClienteAtualId()).thenReturn(7L);when(reference.obterMesReferencia()).thenReturn(YearMonth.of(2026,10));
  var anterior=t(1L,"2026-09-10","DESPESA","40.00","Serviços","Administrativo");
  var atual=t(2L,"2026-10-10","DESPESA",negativo?"-100.00":"100.00","Principal","Principal");
  var receita=t(3L,"2026-10-12","RECEITA","999.00","Serviços","Administrativo");
  when(transactions.findByClienteIdAndDataBetweenOrderByDataAsc(7L,LocalDate.of(2026,9,1),ate)).thenReturn(List.of(anterior,atual,receita));
  when(rates.findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L,List.of(1L,2L))).thenReturn(List.of(
   new FinancialAllocation(atual,1,"Serviços",new BigDecimal("35.00"),"Administrativo",new BigDecimal("35.00")),
   new FinancialAllocation(atual,2,"Outra",new BigDecimal("65.00"),"Operacional",new BigDecimal("65.00"))));
 }
 @Test void filtroCruzadoSelecionaParcelasSemDuplicarLancamento() {
  dados(false);var r=service.consultar(de,ate,"Administrativo","Serviços",null,null);
  assertEquals(new BigDecimal("35.00"),r.total());assertEquals(new BigDecimal("40.00"),r.totalAnterior());assertEquals(1,r.itens().size());assertEquals(new BigDecimal("100.00"),r.itens().get(0).valorIntegral());
  assertEquals(r.total(),r.centros().get(0).atual());assertEquals(r.total(),r.categorias().get(0).atual());assertEquals(new BigDecimal("-5.00"),r.categorias().get(0).diferenca());
 }
 @Test void totaisDasDimensoesConciliamComSelecaoSemReceitas() {
  dados(false);var r=service.consultar(null,null,null,null,null,null);
  assertEquals(new BigDecimal("100.00"),r.total());assertEquals(r.total(),r.centros().stream().map(m->m.atual()).reduce(BigDecimal.ZERO,BigDecimal::add));
  assertEquals(r.total(),r.categorias().stream().map(m->m.atual()).reduce(BigDecimal.ZERO,BigDecimal::add));assertEquals(r.total(),r.itens().stream().map(m->m.valorSelecionado()).reduce(BigDecimal.ZERO,BigDecimal::add));
 }
 @Test void preservaEstornoNosRateiosSelecionados() {
  dados(true);var r=service.consultar(de,ate,"Administrativo",null,null,null);assertEquals(new BigDecimal("-35.00"),r.total());
 }
 @Test void classificacaoAplicaSeAParcelaEAusenciaContinuaNaoClassificada() {
  dados(false);var c=new CostCategoryClassification();c.setClienteId(7L);c.setCategoriaChave("serviços");c.setComportamento(CostBehavior.FIXO);c.setNatureza(CostNature.SERVICOS);when(classifications.findByClienteId(7L)).thenReturn(List.of(c));
  assertEquals(new BigDecimal("35.00"),service.consultar(de,ate,null,null,CostBehavior.FIXO,CostNature.SERVICOS).total());
  assertEquals(new BigDecimal("65.00"),service.consultar(de,ate,null,null,CostBehavior.NAO_CLASSIFICADO,null).total());
 }
 @Test void intervaloPersonalizadoComparaMesmaQuantidadeDeDias() {
  when(context.getClienteAtualId()).thenReturn(7L);var r=service.consultar(LocalDate.of(2026,10,5),LocalDate.of(2026,10,7),null,null,null,null);
  assertEquals(LocalDate.of(2026,10,2),r.inicioAnterior());assertEquals(LocalDate.of(2026,10,4),r.fimAnterior());
 }
 @Test void rejeitaIntervaloInvalidoAntesDeConsultarCliente() {
  assertThrows(IllegalArgumentException.class,()->service.consultar(de,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->service.consultar(de,de.plusMonths(24),null,null,null,null));verifyNoInteractions(context,transactions,rates);
 }
 @Test void consultasSaoLimitadasAoClienteAtual() {
  dados(false);service.consultar(de,ate,null,null,null,null);verify(classifications).findByClienteId(7L);verify(transactions).findByClienteIdAndDataBetweenOrderByDataAsc(7L,LocalDate.of(2026,9,1),ate);
  verify(rates).findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(7L,List.of(1L,2L));
 }
}
