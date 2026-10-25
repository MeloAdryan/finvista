package com.finvista.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.*;import com.finvista.repository.*;
import com.finvista.service.FinancialViewService.*;
import java.math.*;import java.time.*;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialManagementService {
 public record Pending(String parcelaId,String tipo,String descricao,String motivo,UUID execucaoId,Instant avaliadaEm,Long lancamentoId,BigDecimal liquidoAssociado,BigDecimal principalAssociado) {}
 public record Coverage(String fonte,long registros,LocalDate primeiraData,LocalDate ultimaData,Instant atualizadoEm) {}
 public record Health(Long clienteId,Instant consultadoEm,boolean processadorHabilitado,boolean automacaoAtiva,boolean sincronizacaoAmpla,String ultimaSituacao,Instant ultimaExecucao,Instant ultimaConcluida,boolean atualizacaoAtrasada,long totalPendencias,BigDecimal liquidoVinculado,BigDecimal principalSemLiquido,long pendenciasSemValor,List<Pending> pendencias,List<Coverage> fontes,long semSaldoAberto,long semVencimento,String aviso) {}
 public record AgingGroup(String tipo,FinancialOpenItems.Bucket faixa,long quantidade,BigDecimal valorConhecido,long semValor) {}
 public record Aging(Long clienteId,LocalDate referencia,List<AgingGroup> grupos,int total,int pagina,int tamanho,List<FinancialOpenItems.Item> itens,String aviso) {}
 public record ForecastMonth(String mes,BigDecimal recebido,BigDecimal pago,BigDecimal receberPrevisto,BigDecimal pagarPrevisto,BigDecimal receberVencido,BigDecimal pagarVencido) {}
 public record Forecast(Long clienteId,LocalDate hoje,LocalDate inicio,LocalDate fim,String fonte,Total realizado,Total compromissosFuturos,Total vencidosAtuais,List<ForecastMonth> meses,List<FinancialOpenItems.Item> compromissos,Map<String,Integer> exclusoes,String aviso) {}
 public record Payment(String id,LocalDate data,BigDecimal principal,BigDecimal liquido,boolean selecionada) {}
 public record Detail(Long clienteId,String criterio,Long id,String documento,String descricao,String tipo,String origem,String situacao,LocalDate competencia,LocalDate vencimento,BigDecimal principal,BigDecimal realizado,BigDecimal aberto,BigDecimal valorSelecionado,List<Linha> rateios,List<Payment> baixas,String arquivo,Instant importadoEm,List<String> avisos) {}
 private final ClienteContextService ctx;private final FinancialTransactionRepository tx;private final ContaAzulAutoRunRepository runs;private final ContaAzulAutoItemRepository items;private final ContaAzulAutoConfigRepository configs;
 private final StatementMovementRepository statements;private final StatementImportService statementReader;private final ContaAzulApiOperationRepository ops;private final FinancialViewService view;private final ObjectMapper json;private final Clock clock;private final boolean processor;
 public FinancialManagementService(ClienteContextService ctx,FinancialTransactionRepository tx,ContaAzulAutoRunRepository runs,ContaAzulAutoItemRepository items,ContaAzulAutoConfigRepository configs,StatementMovementRepository statements,StatementImportService statementReader,ContaAzulApiOperationRepository ops,FinancialViewService view,ObjectMapper json,Clock clock,@Value("${CONTA_AZUL_AUTO_ENABLED:false}") boolean processor){this.ctx=ctx;this.tx=tx;this.runs=runs;this.items=items;this.configs=configs;this.statements=statements;this.statementReader=statementReader;this.ops=ops;this.view=view;this.json=json;this.clock=clock;this.processor=processor;}
 private void check(Long client){if(!client.equals(ctx.getClienteAtualId()))throw new IllegalArgumentException("A empresa mudou. Consulte novamente.");}
 private LocalDate today(){return LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));}
 private static BigDecimal zero(){return new BigDecimal("0.00");}
 private static Total total(BigDecimal r,BigDecimal d){return new Total(r,d,r.subtract(d));}
 private static LocalDate min(List<LocalDate> d){return d.stream().filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);}
 private static LocalDate max(List<LocalDate> d){return d.stream().filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);}
 @Transactional(readOnly=true) public Health health(){
  Long client=ctx.getClienteAtualId();var transactions=tx.findByClienteId(client);var config=configs.findById(client).orElse(null);var history=runs.findTop10ByClienteIdOrderByCriadoEmDesc(client);var latest=history.isEmpty()?null:history.get(0);
  var completed=runs.findFirstByClienteIdAndStatusInOrderByConcluidoEmDesc(client,List.of("CONCLUIDA","CONCLUIDA_COM_PENDENCIAS")).orElse(null);
  Instant last=completed==null?null:completed.concluidoEm;long threshold=Math.max(60,config==null?60:config.intervaloMinutos*2L);boolean stale=last==null||last.plusSeconds(threshold*60).isBefore(clock.instant());
  Map<Long,FinancialTransaction> byId=new HashMap<>();for(var t:transactions)byId.put(t.getId(),t);
  List<Pending> pending=new ArrayList<>();Set<String> seen=new HashSet<>();Set<Long> counted=new HashSet<>();BigDecimal liquid=zero(),principal=zero();long noValue=0;
  for(var i:items.findTerminalByCliente(client)){
   String key=(i.tipo==null?"":i.tipo)+":"+(i.parcelaId==null?i.id:i.parcelaId.toLowerCase(Locale.ROOT));if(!seen.add(key)||!"CONFERIR".equals(i.status))continue;
   var t=byId.get(i.lancamentoId);BigDecimal l=null,p=null;
   if(t!=null&&FinancialStatusPolicy.ativo(t)){
    if(t.getValorTotalAberto()!=null&&t.getValorTotalRealizado()!=null)l=t.getValorTotalAberto().add(t.getValorTotalRealizado());else p=t.getValor();
    if(counted.add(t.getId())){if(l!=null)liquid=liquid.add(l);else if(p!=null)principal=principal.add(p);}
   }
   if(l==null&&p==null)noValue++;
   pending.add(new Pending(i.parcelaId,i.tipo,i.descricao,i.mensagem,i.execucaoId,i.concluidoEm,i.lancamentoId,l,p));
  }
  List<Coverage> coverage=new ArrayList<>();var api=transactions.stream().filter(t->"CONTA_AZUL_API".equals(t.getOrigem())).toList();coverage.add(new Coverage("Parcelas API · competência",api.size(),min(api.stream().map(FinancialTransaction::getDataCompetencia).toList()),max(api.stream().map(FinancialTransaction::getDataCompetencia).toList()),last));
  coverage.add(new Coverage("Parcelas API · vencimento",api.size(),min(api.stream().map(FinancialTransaction::getDataVencimento).toList()),max(api.stream().map(FinancialTransaction::getDataVencimento).toList()),last));
  List<LocalDate> dates=new ArrayList<>();Instant fileTime=null;int invalidFiles=0;var saved=statements.findByClienteIdOrderByIdAsc(client);
  for(var row:saved){try{dates.add(statementReader.decode(row).movimento());}catch(Exception e){invalidFiles++;}if(row.getImportadoEm()!=null&&(fileTime==null||row.getImportadoEm().isAfter(fileTime)))fileTime=row.getImportadoEm();}
  coverage.add(new Coverage("Extratos · data de movimento",saved.size(),min(dates),max(dates),fileTime));
  var opens=FinancialOpenItems.read(transactions,today());long missing=opens.stream().filter(x->x.aberto()==null).count(),noDue=opens.stream().filter(x->x.vencimento()==null).count();check(client);
  return new Health(client,clock.instant(),processor,config!=null&&config.habilitada,config!=null&&Boolean.TRUE.equals(config.sincronizacaoCompleta),latest==null?"SEM_EXECUCAO":latest.status,latest==null?null:latest.criadoEm,last,stale,pending.size(),liquid,principal,noValue,List.copyOf(pending),List.copyOf(coverage),missing,noDue,
   "Saúde da base local inteira. Faixas de datas não comprovam cobertura de todos os dias/contas do ERP. Pendências são a última avaliação terminal de cada parcela; itens apenas em fila não encerram ocorrências antigas. Valores vinculados são os valores cadastrados associados, não uma estimativa da divergência financeira. Extratos ilegíveis: "+invalidFiles+". A atualização é sinalizada após "+threshold+" minutos desde a última varredura concluída.");
 }
 @Transactional(readOnly=true) public Aging aging(String tipo,String bucket,int page,int size){
  if(page<0||size<1||size>50)throw new IllegalArgumentException("Paginação inválida.");if(tipo!=null&&!Set.of("RECEITA","DESPESA").contains(tipo))throw new IllegalArgumentException("Tipo inválido.");
  FinancialOpenItems.Bucket selected=bucket==null?null:FinancialOpenItems.Bucket.valueOf(bucket);Long client=ctx.getClienteAtualId();LocalDate day=today();var all=FinancialOpenItems.read(tx.findByClienteId(client),day);List<AgingGroup> groups=new ArrayList<>();
  for(String type:List.of("RECEITA","DESPESA"))for(var band:FinancialOpenItems.Bucket.values()){
   var rows=all.stream().filter(x->type.equals(x.tipo())&&x.faixa()==band).toList();groups.add(new AgingGroup(type,band,rows.size(),rows.stream().map(FinancialOpenItems.Item::aberto).filter(Objects::nonNull).reduce(zero(),BigDecimal::add),rows.stream().filter(x->x.aberto()==null).count()));
  }
  var filtered=all.stream().filter(x->(tipo==null||tipo.equals(x.tipo()))&&(selected==null||selected==x.faixa())).toList();var list=filtered.stream().skip((long)page*size).limit(size).toList();check(client);
  return new Aging(client,day,List.copyOf(groups),filtered.size(),page,size,list,"Posição atual de todos os vencimentos. Saldos líquidos em aberto, não o valor original de parcelas parcialmente pagas. Sem saldo ou vencimento suficiente aparece em grupo próprio; não é interpretado como zero.");
 }
 @Transactional(readOnly=true) public Forecast forecast(LocalDate start,LocalDate end,Criterio source){
  if(start==null||end==null||start.isAfter(end)||start.getYear()<1900||end.getYear()>2200)throw new IllegalArgumentException("Informe um período entre 1900 e 2200.");
  if(source!=Criterio.BAIXA&&source!=Criterio.BAIXA_EXTRATO)throw new IllegalArgumentException("Escolha baixas API ou extrato.");
  Long client=ctx.getClienteAtualId();var actual=view.consultar(new Filtro(source,Periodo.PERSONALIZADO,Situacao.TODAS,null,start,end,null,null));if(!client.equals(actual.clienteId()))throw new IllegalArgumentException("A empresa mudou.");LocalDate day=today();
  Map<String,BigDecimal[]> months=new TreeMap<>();for(var m=YearMonth.from(start);!m.isAfter(YearMonth.from(end));m=m.plusMonths(1))months.put(m.toString(),new BigDecimal[]{zero(),zero(),zero(),zero(),zero(),zero()});
  for(var l:actual.linhas()){var v=months.get(YearMonth.from(l.data()).toString());if(v!=null)v["RECEITA".equals(l.tipo())?0:1]=v["RECEITA".equals(l.tipo())?0:1].add(l.valor());}
  int missing=0;List<FinancialOpenItems.Item> commitments=new ArrayList<>();
  for(var o:FinancialOpenItems.read(tx.findByClienteId(client),day)){
   if(o.aberto()==null||o.vencimento()==null){missing++;continue;}if(o.vencimento().isBefore(start)||o.vencimento().isAfter(end))continue;
   commitments.add(o);int index=(o.vencimento().isBefore(day)?4:2)+("DESPESA".equals(o.tipo())?1:0);var v=months.get(YearMonth.from(o.vencimento()).toString());v[index]=v[index].add(o.aberto());
  }
  List<ForecastMonth> rows=new ArrayList<>();BigDecimal[] sums={zero(),zero(),zero(),zero(),zero(),zero()};for(var e:months.entrySet()){var v=e.getValue();for(int i=0;i<6;i++)sums[i]=sums[i].add(v[i]);rows.add(new ForecastMonth(e.getKey(),v[0],v[1],v[2],v[3],v[4],v[5]));}
  var exclusions=new TreeMap<>(actual.exclusoes());exclusions.put("Compromissos sem saldo líquido ou vencimento",missing);check(client);
  return new Forecast(client,day,start,end,source.name(),actual.totais(),total(sums[2],sums[3]),total(sums[4],sums[5]),List.copyOf(rows),List.copyOf(commitments),exclusions,
   "Realizado usa a data efetiva da fonte escolhida. Previsto contém somente saldo líquido ainda aberto, com vencimento hoje ou depois, dentro do recorte. Vencidos são exposição atual separada, sem atribuir uma nova data de pagamento. Não somar API e extrato; simulações e saldos bancários não integram estes valores. A posição em aberto é a de hoje, mesmo ao consultar um período passado.");
 }
 @Transactional(readOnly=true) public Detail detail(Long id,String movement,Filtro filter,boolean position){
  if(id==null||id<1)throw new IllegalArgumentException("Registro inválido.");Long client=ctx.getClienteAtualId();List<Linha> lines=List.of();BigDecimal selected=null;
  if(!position){var data=view.consultar(filter);if(!client.equals(data.clienteId()))throw new IllegalArgumentException("A empresa mudou.");lines=data.linhas().stream().filter(l->id.equals(l.id())&&(movement==null||Objects.equals(movement,l.movimentoId()))).toList();if(lines.isEmpty())throw new IllegalArgumentException("Registro fora da empresa, critério ou recorte selecionado.");selected=lines.stream().map(Linha::valor).reduce(zero(),BigDecimal::add);}
  if(!position&&filter.criterio()==Criterio.BAIXA_EXTRATO){var saved=statements.findByClienteIdOrderByIdAsc(client).stream().filter(x->id.equals(x.getId())).findFirst().orElseThrow(()->new IllegalArgumentException("Movimento não encontrado."));var l=statementReader.decode(saved);check(client);return new Detail(client,filter.criterio().name(),id,lines.get(0).documento(),l.descricao(),l.tipo(),"EXTRATO",l.situacao(),l.competencia(),l.vencimento(),null,null,null,selected,lines,List.of(),saved.getArquivo(),saved.getImportadoEm(),List.of("Uma movimentação do extrato não comprova o valor integral nem o saldo aberto de uma parcela."));}
  var t=tx.findByClienteId(client).stream().filter(x->id.equals(x.getId())).findFirst().orElseThrow(()->new IllegalArgumentException("Lançamento não encontrado nesta empresa."));
  if(position){var o=FinancialOpenItems.read(List.of(t),today());if(o.isEmpty())throw new IllegalArgumentException("O registro não está em aberto.");selected=o.get(0).aberto();}
  List<Payment> payments=new ArrayList<>();List<String> warnings=new ArrayList<>();
  if("CONTA_AZUL_API".equals(t.getOrigem()))try{
   var snapshots=ops.snapshotsAtuais(client).stream().filter(x->id.equals(x.getLancamentoId())).toList();var op=snapshots.isEmpty()?null:snapshots.get(0);boolean ambiguous=snapshots.stream().anyMatch(x->!Objects.equals(x.getOrigemSnapshot(),op.getOrigemSnapshot()));
   var audited=AuditedSettlementReader.read(t,op,ambiguous,json,today());for(var p:audited.pagamentos())payments.add(new Payment(p.id(),p.data(),p.principal(),p.liquido(),movement!=null&&movement.equals(p.id())));
  }catch(Exception e){warnings.add("Baixas sem composição auditada utilizável; nenhum pagamento foi estimado.");}
  else warnings.add("Fonte sem histórico de baixas API auditado. Campos de pago/aberto são valores cadastrados, não uma lista de pagamentos individuais.");
  check(client);return new Detail(client,position?"POSICAO_ATUAL":filter.criterio().name(),id,t.getDocumentoReferencia(),t.getDescricao(),t.getTipo(),t.getOrigem(),t.getSituacao(),t.getDataCompetencia(),t.getDataVencimento(),t.getValor(),t.getValorTotalRealizado(),t.getValorTotalAberto(),selected,lines,List.copyOf(payments),null,null,List.copyOf(warnings));
 }
}
