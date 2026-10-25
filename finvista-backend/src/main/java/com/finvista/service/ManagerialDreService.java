package com.finvista.service;
import com.finvista.model.DreCategoryMapping;import com.finvista.repository.DreCategoryMappingRepository;import com.finvista.service.FinancialViewService.*;
import java.math.BigDecimal;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
public class ManagerialDreService {
 public enum Group {RECEITA_OPERACIONAL,DEDUCOES_RECEITA,CUSTOS,DESPESAS_OPERACIONAIS,RECEITA_FINANCEIRA,DESPESA_FINANCEIRA,FORA_DRE}
 public record MappingRequest(Long clienteId,String tipo,String categoria,Group grupo,boolean confirmado,Long versao,String observacao) {}
 public record Category(String tipo,String categoria,BigDecimal valor,Group grupo,UUID id,Long versao,Instant revisadoEm) {}
 public record Row(String nome,BigDecimal valor) {}
 public record Result(Long clienteId,LocalDate inicio,LocalDate fim,String estado,List<Category> categorias,List<Row> demonstrativo,long naoMapeadas,BigDecimal foraReceitas,BigDecimal foraDespesas,Map<String,Integer> exclusoes,String aviso) {}
 private final ClienteContextService ctx;private final FinancialViewService view;private final DreCategoryMappingRepository mappings;private final Clock clock;
 public ManagerialDreService(ClienteContextService ctx,FinancialViewService view,DreCategoryMappingRepository mappings,Clock clock){this.ctx=ctx;this.view=view;this.mappings=mappings;this.clock=clock;}
 static String key(String type,String category){return type+":"+category.trim().toLowerCase(Locale.ROOT);}
 static boolean accepts(Group group,String type){return group==Group.FORA_DRE||("RECEITA".equals(type)?Set.of(Group.RECEITA_OPERACIONAL,Group.RECEITA_FINANCEIRA).contains(group):Set.of(Group.DEDUCOES_RECEITA,Group.CUSTOS,Group.DESPESAS_OPERACIONAIS,Group.DESPESA_FINANCEIRA).contains(group));}
 @Transactional public DreCategoryMapping save(MappingRequest request){
  ctx.validarAdministrador();Long client=ctx.getClienteAtualId();
  if(request==null||!client.equals(request.clienteId())||!request.confirmado()||request.grupo()==null||!Set.of("RECEITA","DESPESA").contains(request.tipo()==null?"":request.tipo())||request.categoria()==null||request.categoria().isBlank()||request.categoria().trim().length()>255||!accepts(request.grupo(),request.tipo()))throw new IllegalArgumentException("Revise tipo, categoria e grupo e confirme a validação do mapeamento.");
  if(request.observacao()!=null&&request.observacao().length()>1000)throw new IllegalArgumentException("Observação muito longa.");
  if(request.grupo()==Group.FORA_DRE&&(request.observacao()==null||request.observacao().isBlank()))throw new IllegalArgumentException("Justifique a classificação fora da DRE.");
  String categoryKey=key(request.tipo(),request.categoria());UUID id=UUID.nameUUIDFromBytes((client+":"+categoryKey).getBytes(StandardCharsets.UTF_8));var found=mappings.findById(id);var row=found.orElseGet(DreCategoryMapping::new);
  if(found.isPresent()&&(!client.equals(row.clienteId)||request.versao()==null||request.versao()!=row.versao))throw new IllegalArgumentException("O mapeamento mudou. Consulte novamente antes de salvar.");
  row.id=id;row.clienteId=client;row.categoriaChave=categoryKey;row.tipo=request.tipo();row.categoria=request.categoria().trim();row.grupo=request.grupo().name();row.observacao=request.observacao();row.revisadoEm=clock.instant();row.revisadoPor=ctx.getUsuarioAutenticado().getId();
  if(!client.equals(ctx.getClienteAtualId()))throw new IllegalArgumentException("A empresa mudou.");return mappings.saveAndFlush(row);
 }
 @Transactional public void remove(UUID id,long version){ctx.validarAdministrador();Long client=ctx.getClienteAtualId();var row=mappings.findById(id).orElseThrow(()->new IllegalArgumentException("Mapeamento não encontrado."));if(!client.equals(row.clienteId)||row.versao!=version)throw new IllegalArgumentException("Mapeamento fora da empresa ou alterado. Consulte novamente.");mappings.delete(row);}
 @Transactional(readOnly=true) public Result report(LocalDate start,LocalDate end){
  Long client=ctx.getClienteAtualId();var data=view.consultar(new Filtro(Criterio.COMPETENCIA,Periodo.PERSONALIZADO,Situacao.TODAS,null,start,end,null,null));if(!client.equals(data.clienteId()))throw new IllegalArgumentException("A empresa mudou.");
  Map<String,DreCategoryMapping> config=new HashMap<>();for(var m:mappings.findByClienteIdOrderByTipoAscCategoriaAsc(client))config.put(m.categoriaChave,m);
  Map<String,List<Linha>> categories=new TreeMap<>();for(var l:data.linhas())categories.computeIfAbsent(key(l.tipo(),l.categoria()),k->new ArrayList<>()).add(l);
  List<Category> rows=new ArrayList<>();Map<Group,BigDecimal> values=new EnumMap<>(Group.class);for(var g:Group.values())values.put(g,BigDecimal.ZERO);long missing=0;BigDecimal outsideR=BigDecimal.ZERO,outsideD=BigDecimal.ZERO;
  for(var e:categories.entrySet()){
   var first=e.getValue().get(0);var amount=e.getValue().stream().map(Linha::valor).reduce(BigDecimal.ZERO,BigDecimal::add);var mapping=config.get(e.getKey());Group group=null;
   if(mapping!=null)try{group=Group.valueOf(mapping.grupo);if(!accepts(group,first.tipo()))group=null;}catch(IllegalArgumentException ex){group=null;}
   if(group==null)missing++;else if(group==Group.FORA_DRE){if("RECEITA".equals(first.tipo()))outsideR=outsideR.add(amount);else outsideD=outsideD.add(amount);}else values.put(group,values.get(group).add(amount));
   rows.add(new Category(first.tipo(),first.categoria(),amount,group,mapping==null?null:mapping.id,mapping==null?null:mapping.versao,mapping==null?null:mapping.revisadoEm));
  }
  var gross=values.get(Group.RECEITA_OPERACIONAL);var deductions=values.get(Group.DEDUCOES_RECEITA);var net=gross.subtract(deductions);var costs=values.get(Group.CUSTOS);var grossResult=net.subtract(costs);var operating=values.get(Group.DESPESAS_OPERACIONAIS);var operatingResult=grossResult.subtract(operating);var finR=values.get(Group.RECEITA_FINANCEIRA);var finD=values.get(Group.DESPESA_FINANCEIRA);var result=operatingResult.add(finR).subtract(finD);
  var statement=List.of(new Row("Receita operacional",gross),new Row("Deduções da receita",deductions),new Row("Receita líquida gerencial",net),new Row("Custos",costs),new Row("Resultado bruto gerencial",grossResult),new Row("Despesas operacionais",operating),new Row("Resultado operacional gerencial",operatingResult),new Row("Receitas financeiras",finR),new Row("Despesas financeiras",finD),new Row("Resultado gerencial do recorte",result));
  if(!client.equals(ctx.getClienteAtualId()))throw new IllegalArgumentException("A empresa mudou.");
  return new Result(client,start,end,rows.isEmpty()?"SEM_REGISTROS":missing>0?"MAPEAMENTO_PENDENTE":"MAPEAMENTO_REVISADO",List.copyOf(rows),missing>0||rows.isEmpty()?List.of():statement,missing,outsideR,outsideD,data.exclusoes(),
   "Prévia gerencial por competência e principal. O demonstrativo só é liberado quando todas as categorias presentes no recorte têm mapeamento explicitamente confirmado. Isso não comprova cobertura integral nem equivalência ao DRE contábil/fiscal do Conta Azul. Exclusões fora da DRE aparecem separadamente; o mapeamento não altera os lançamentos.");
 }
}
