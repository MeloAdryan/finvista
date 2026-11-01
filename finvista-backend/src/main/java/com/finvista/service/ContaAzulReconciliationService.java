package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaAzulReconciliationService {

    private final ClienteContextService context;
    private final FinancialViewService view;
    @org.springframework.beans.factory.annotation.Autowired
    private com.finvista.repository.StatementMovementRepository statements;
    @org.springframework.beans.factory.annotation.Autowired
    private StatementImportService statementImport;

    public ContaAzulReconciliationService(ClienteContextService context, FinancialViewService view) {
        this.context = context;
        this.view = view;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.finvista.repository.ContaAzulAutoItemRepository autoItems;
    @org.springframework.beans.factory.annotation.Autowired
    private com.finvista.repository.ContaAzulAutoRunRepository autoRuns;

    public record Resumo(int movimentosApi, int movimentosExtrato, int porId, int sugestoes,
            int divergencias, int ambiguidades, int contasNaoComparaveis, boolean totaisReproduzidos, int porConferencia) {}

    public record AccountSummary(String contaId,String nome,boolean identificada,int movimentosApi,int movimentosExtrato,
            BigDecimal receitaApi,BigDecimal despesaApi,BigDecimal receitaExtrato,BigDecimal despesaExtrato) {}

    public record Cobertura(String fonte, String ultimaSituacao, java.time.Instant ultimoTermino,
            Integer pendenciasEmpresa, boolean consultaAoVivo, boolean coberturaConfirmada) {}

    public record Resultado(Long clienteId, LocalDate inicio, LocalDate fim, String situacao,
            FinancialViewService.Total api, FinancialViewService.Total extrato, BigDecimal diferencaReceita,
            BigDecimal diferencaDespesa, int linhasApi, int linhasExtrato, Map<String, Integer> exclusoesApi,
            Map<String, Integer> exclusoesExtrato, String aviso, FinancialMovementMatcher.Result detalhes, Resumo resumo, Cobertura cobertura, java.util.List<AccountSummary> contas) {}

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Resultado conferir(Long clienteId, LocalDate inicio, LocalDate fim) {
        context.validarAdministrador();
        return conferirLeitura(clienteId, inicio, fim);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Resultado conferirLeitura(Long clienteId, LocalDate inicio, LocalDate fim) {
        if (clienteId == null || !clienteId.equals(context.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou.");
        }
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new IllegalArgumentException("Informe um período válido.");
        }
        var api = view.consultar(filtro(FinancialViewService.Criterio.BAIXA, inicio, fim));
        var file = view.consultar(filtro(FinancialViewService.Criterio.BAIXA_EXTRATO, inicio, fim));
        if (!clienteId.equals(api.clienteId()) || !clienteId.equals(file.clienteId()) || !clienteId.equals(context.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa mudou durante a conferência.");
        }
        BigDecimal receita = api.totais().receita().subtract(file.totais().receita());
        BigDecimal despesa = api.totais().despesa().subtract(file.totais().despesa());
        String status = api.linhas().isEmpty() || file.linhas().isEmpty() ? "SEM_DADOS_COMPARAVEIS"
                : receita.signum() != 0 || despesa.signum() != 0 ? "DIVERGENCIA" : "TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS";
        var fields = new java.util.HashMap<String, java.util.Map<String, String>>();
        if (statements != null && statementImport != null) {
            for (var row : statements.findByClienteIdOrderByIdAsc(clienteId)) {
                fields.put("EXTRATO:" + row.getChave() + ":" + row.getOcorrencia(), statementImport.decode(row).campos());
            }
        }
        var apiMovements = accountNames(clienteId,movements(api.linhas(), java.util.Map.of(), true));
        var originals = new java.util.HashMap<String, com.finvista.model.StatementMovement>();
        if(statements!=null) for(var row:statements.findByClienteIdOrderByIdAsc(clienteId))
            originals.put("EXTRATO:"+row.getChave()+":"+row.getOcorrencia(),row);
        var fileMovements = reviewed(clienteId,movements(file.linhas(), fields, false),apiMovements,originals);
        var details = FinancialMovementMatcher.compare(apiMovements, fileMovements);
        boolean integrity = totalsMatch(apiMovements, api.totais()) && totalsMatch(fileMovements, file.totais());
        int byId = (int) details.correspondencias().stream().filter(m -> m.criterio().equals("ID_BAIXA_DATA_TIPO_VALOR")).count();
        int manual=(int) details.correspondencias().stream().filter(m->m.criterio().equals("VINCULO_CONFIRMADO_PELO_CONTADOR")).count();
        int suggestions = details.correspondencias().size() - byId - manual;
        int unknownAccounts=(int) fileMovements.stream().filter(x->details.correspondencias().stream()
                .noneMatch(m->m.extrato().chave().equals(x.chave()) && m.contaConferida())).count();
        if ("TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS".equals(status) && (!details.divergencias().isEmpty() || !details.ambiguidades().isEmpty()
                || !details.somenteApi().isEmpty() || !details.somenteExtrato().isEmpty())) {
            status = "DIVERGENCIA_DE_REGISTROS";
        }
        if ("TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS".equals(status) && suggestions == 0 && integrity && manual == 0) {
            status = "VALORES_POR_ID_CONFERIDOS_COBERTURA_NAO_CERTIFICADA";
        }
        if("TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS".equals(status) && suggestions==0 && integrity && manual>0)
            status="VINCULOS_CONFERIDOS_COBERTURA_NAO_CERTIFICADA";
        var summary = new Resumo(apiMovements.size(), fileMovements.size(), byId, suggestions, details.divergencias().size(), details.ambiguidades().size(), unknownAccounts, integrity,manual);
        Integer pending = null;
        if (autoItems != null) {
            Set<String> seen = new java.util.HashSet<>();
            int count = 0;
            for (var item : autoItems.findTerminalByCliente(clienteId)) {
                String key = item.parcelaId == null ? "" : item.parcelaId.trim().toLowerCase(java.util.Locale.ROOT);
                if (!key.isEmpty() && seen.add(key) && "CONFERIR".equals(item.status)) {
                    count++;
                }
            }
            pending = count;
        }
        var runs = autoRuns == null ? java.util.List.<com.finvista.model.ContaAzulAutoRun>of() : autoRuns.findTop10ByClienteIdOrderByCriadoEmDesc(clienteId);
        var latest = runs.isEmpty() ? null : runs.get(0);
        var coverage = new Cobertura("BAIXAS_ARMAZENADAS", latest == null ? null : latest.status, latest == null ? null : latest.concluidoEm, pending, false, false);
        if (!clienteId.equals(context.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa mudou durante a conferência.");
        }
        return new Resultado(clienteId, inicio, fim, status, api.totais(), file.totais(), receita, despesa, api.linhas().size(), file.linhas().size(), api.exclusoes(), file.exclusoes(),
                "Conferência de data de baixa/movimento e líquido das receitas/despesas disponíveis, com transferências fora desses indicadores. Igualdade dos totais não comprova correspondência linha a linha nem cobertura integral. Arquivos filtrados e exclusões podem produzir diferenças. As exclusões se referem à base inteira da fonte, antes do recorte. Nenhum movimento foi somado entre fontes. Vínculos manuais são separados dos IDs informados no arquivo e só permanecem válidos enquanto os dados conferidos não mudarem. As sugestões por data/tipo/valor não certificam identidade; ambiguidades exigem revisão. Divergências por ID mostram os campos afetados. Contas sem ID nos dois lados não são certificadas. Pendências referem-se à empresa inteira. Datas fora do recorte podem aparecer somente em uma fonte; amplie o período para investigar.", details, summary, coverage,accounts(apiMovements,fileMovements));
    }

    private static java.util.List<FinancialMovementMatcher.Movement> movements(java.util.List<FinancialViewService.Linha> lines, java.util.Map<String, java.util.Map<String, String>> fields, boolean api) {
        var groups = new java.util.LinkedHashMap<String, java.util.List<FinancialViewService.Linha>>();
        for (var l : lines) {
            var key = l.id() + ":" + l.documento() + ":" + l.movimentoId() + ":" + l.data() + ":" + l.tipo();
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(l);
        }
        var result = new java.util.ArrayList<FinancialMovementMatcher.Movement>();
        for (var e : groups.entrySet()) {
            var l = e.getValue().get(0);
            var f = fields.getOrDefault(l.documento(), java.util.Map.of());
            String parcel = api ? l.documento() : field(f, "id da parcela", "id parcela", "id_parcela");
            String payment = api ? l.movimentoId() : field(f, "id da baixa", "id baixa", "id_baixa");
            var amount = e.getValue().stream().map(FinancialViewService.Linha::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            String account = api ? l.contaId() : field(f, "id da conta financeira", "id conta financeira", "id_conta_financeira");
            result.add(new FinancialMovementMatcher.Movement(e.getKey(), parcel, payment, l.descricao(), l.tipo(), l.data(), amount, account,api?null:field(f,"conta bancaria","conta bancária"),null));
        }
        return result;
    }

    private static String field(java.util.Map<String, String> fields, String... names) {
        var values = new java.util.LinkedHashMap<String, String>();
        for (var entry : fields.entrySet()) {
            for (String name : names) {
                String key = ContaAzulStatementService.normalize(entry.getKey()).replace('_', ' ');
                if (key.equals(ContaAzulStatementService.normalize(name).replace('_', ' ')) && entry.getValue() != null && !entry.getValue().isBlank()) {
                    values.putIfAbsent(entry.getValue().trim().toLowerCase(java.util.Locale.ROOT), entry.getValue().trim());
                }
            }
        }
        if (values.size() > 1) {
            throw new IllegalArgumentException("Colunas de identificação do extrato divergem; confira o documento original.");
        }
        return values.isEmpty() ? null : values.values().iterator().next();
    }

    private static boolean totalsMatch(java.util.List<FinancialMovementMatcher.Movement> rows, FinancialViewService.Total expected) {
        BigDecimal receipt = BigDecimal.ZERO, expense = BigDecimal.ZERO;
        for (var row : rows) {
            if ("RECEITA".equals(row.tipo())) {
                receipt = receipt.add(row.valor()); 
            }else if ("DESPESA".equals(row.tipo())) {
                expense = expense.add(row.valor());
            }
        }
        return receipt.compareTo(expected.receita()) == 0 && expense.compareTo(expected.despesa()) == 0
                && receipt.subtract(expense).compareTo(expected.resultado()) == 0;
    }

    private static FinancialViewService.Filtro filtro(FinancialViewService.Criterio tipo, LocalDate inicio, LocalDate fim) {
        return new FinancialViewService.Filtro(tipo, FinancialViewService.Periodo.PERSONALIZADO, FinancialViewService.Situacao.TODAS, fim, inicio, fim, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired private com.finvista.repository.ContaAzulApiOperationRepository operations;
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @org.springframework.beans.factory.annotation.Autowired private java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired private FinancialAccountsService financialAccounts;
    private static final String PREFIX="finvista:conciliacao ";
    public record LinkRequest(Long clienteId,LocalDate inicio,LocalDate fim,String extratoChave,String apiChave,String acao,String motivo) {}
    public record LinkPlan(LinkRequest pedido,FinancialMovementMatcher.Movement api,FinancialMovementMatcher.Movement extrato,
            String assinaturaApi,String assinaturaExtrato,String estado,Long usuarioId) {}

    private static String text(String value){return value==null?"":value.trim();}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Falha ao conferir os dados.",e);}}
    private String apiSignature(FinancialMovementMatcher.Movement m){return StatementImportService.hash(encode(java.util.Arrays.asList(m.chave(),m.parcelaId(),m.baixaId(),m.tipo(),m.data(),m.valor().stripTrailingZeros().toPlainString(),m.contaId())));}
    private String fileSignature(com.finvista.model.StatementMovement row){
        var l=statementImport.decode(row);var fields=new java.util.TreeMap<String,String>();
        if(l.campos()!=null)l.campos().forEach((k,v)->{if(!k.startsWith("finvista:"))fields.put(k,v);});
        return StatementImportService.hash(encode(java.util.Arrays.asList(row.getId(),row.getChave(),row.getOcorrencia(),
            l.movimento(),l.competencia(),l.vencimento(),l.descricao(),l.tipo(),l.origem(),l.conta(),l.situacao(),l.valor(),l.rateios(),fields)));
    }
    private String rowState(com.finvista.model.StatementMovement row){return StatementImportService.hash(row.getConteudo());}
    private java.util.List<FinancialMovementMatcher.Movement> accountNames(Long client,java.util.List<FinancialMovementMatcher.Movement> rows){
        Map<String,String> names=new java.util.HashMap<>();
        if(operations!=null && json!=null)for(var op:operations.snapshotsAtuais(client)){
            try{var node=json.readTree(op.getOrigemSnapshot());for(var payment:node.path("baixas")){
                var account=payment.path("conta_financeira");String id=account.path("id").asText("");String name=account.path("nome").asText("");
                if(!id.isBlank()&&!name.isBlank())names.putIfAbsent(id.toLowerCase(java.util.Locale.ROOT),name);
            }}catch(com.fasterxml.jackson.core.JsonProcessingException e){ /* Missing labels do not certify account identity. */ }
        }
        if(financialAccounts!=null)financialAccounts.nomesEmCache(client).forEach((id,name)->names.put(id.toLowerCase(java.util.Locale.ROOT),name));
        return rows.stream().map(m->new FinancialMovementMatcher.Movement(m.chave(),m.parcelaId(),m.baixaId(),m.descricao(),m.tipo(),m.data(),m.valor(),m.contaId(),
            names.get(text(m.contaId()).toLowerCase(java.util.Locale.ROOT)),null)).toList();
    }
    private java.util.List<FinancialMovementMatcher.Movement> reviewed(Long client,java.util.List<FinancialMovementMatcher.Movement> file,
            java.util.List<FinancialMovementMatcher.Movement> api,Map<String,com.finvista.model.StatementMovement> originals){
        var result=new java.util.ArrayList<FinancialMovementMatcher.Movement>();
        for(var f:file){
            var parts=f.chave().split(":",5);
            var row=parts.length>=4?originals.get(parts[1]+":"+parts[2]+":"+parts[3]):null;
            if(row==null){result.add(f);continue;}
            var meta=statementImport.decode(row).campos();
            if(meta==null || !"ATIVO".equals(meta.get(PREFIX+"estado"))){result.add(f);continue;}
            String payment=text(meta.get(PREFIX+"baixa"));
            var candidates=api.stream().filter(a->text(a.baixaId()).equalsIgnoreCase(payment)).toList();
            var selected=candidates.size()==1?candidates.get(0):null;
            boolean valid=selected!=null && String.valueOf(client).equals(meta.get(PREFIX+"empresa"))
                && apiSignature(selected).equals(meta.get(PREFIX+"assinatura api")) && fileSignature(row).equals(meta.get(PREFIX+"assinatura extrato"))
                && text(selected.contaId()).equalsIgnoreCase(text(meta.get(PREFIX+"conta")))
                && text(selected.parcelaId()).equalsIgnoreCase(text(meta.get(PREFIX+"parcela")))
                && (text(f.baixaId()).isEmpty() || text(f.baixaId()).equalsIgnoreCase(payment))
                && (text(f.contaId()).isEmpty() || text(f.contaId()).equalsIgnoreCase(text(meta.get(PREFIX+"conta"))));
            var review=new FinancialMovementMatcher.Review(valid,text(meta.get(PREFIX+"motivo")),text(meta.get(PREFIX+"usuario")),text(meta.get(PREFIX+"horario")));
            result.add(new FinancialMovementMatcher.Movement(f.chave(),text(f.parcelaId()).isEmpty()?meta.get(PREFIX+"parcela"):f.parcelaId(),
                text(f.baixaId()).isEmpty()?payment:f.baixaId(),f.descricao(),f.tipo(),f.data(),f.valor(),
                valid?meta.get(PREFIX+"conta"):f.contaId(),f.contaNome(),review));
        }
        return java.util.List.copyOf(result);
    }
    private static java.util.List<AccountSummary> accounts(java.util.List<FinancialMovementMatcher.Movement> api,java.util.List<FinancialMovementMatcher.Movement> file){
        Map<String,java.util.List<FinancialMovementMatcher.Movement>> a=new java.util.TreeMap<>(),f=new java.util.TreeMap<>();
        java.util.function.Function<FinancialMovementMatcher.Movement,String> key=m->text(m.contaId()).isEmpty()?"SEM_ID:"+text(m.contaNome()).toLowerCase(java.util.Locale.ROOT):text(m.contaId()).toLowerCase(java.util.Locale.ROOT);
        for(var m:api)a.computeIfAbsent(key.apply(m),x->new java.util.ArrayList<>()).add(m);
        for(var m:file)f.computeIfAbsent(key.apply(m),x->new java.util.ArrayList<>()).add(m);
        var keys=new java.util.TreeSet<String>();keys.addAll(a.keySet());keys.addAll(f.keySet());
        var result=new java.util.ArrayList<AccountSummary>();
        for(var k:keys){var aa=a.getOrDefault(k,java.util.List.of());var ff=f.getOrDefault(k,java.util.List.of());
            var name=java.util.stream.Stream.concat(aa.stream(),ff.stream()).map(FinancialMovementMatcher.Movement::contaNome).filter(n->!text(n).isEmpty()).findFirst().orElse("Nome da conta não informado");
            boolean known=!k.startsWith("SEM_ID:");
            result.add(new AccountSummary(known?k:null,name,known,aa.size(),ff.size(),sum(aa,"RECEITA"),sum(aa,"DESPESA"),sum(ff,"RECEITA"),sum(ff,"DESPESA")));
        }return java.util.List.copyOf(result);
    }
    private static BigDecimal sum(java.util.List<FinancialMovementMatcher.Movement> rows,String type){return rows.stream().filter(x->type.equals(x.tipo())).map(FinancialMovementMatcher.Movement::valor).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private com.finvista.model.StatementMovement row(Long client,String key){
        return statements.findByClienteIdOrderByIdAsc(client).stream().filter(r->key.startsWith(r.getId()+":EXTRATO:"+r.getChave()+":"+r.getOcorrencia()+":")).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Movimento não encontrado nesta empresa."));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public LinkPlan planejarVinculo(LinkRequest p){
        context.validarAdministrador();
        if(p==null || !java.util.Objects.equals(p.clienteId(),context.getClienteAtualId()))throw new IllegalArgumentException("A empresa mudou.");
        String reason=text(p.motivo());if(reason.length()<15||reason.length()>1000)throw new IllegalArgumentException("Informe a referência e a conferência do documento original (15 a 1000 caracteres).");
        if(!java.util.Set.of("VINCULAR","DESVINCULAR").contains(text(p.acao())))throw new IllegalArgumentException("Ação de conferência inválida.");
        var report=conferirLeitura(p.clienteId(),p.inicio(),p.fim());
        var file=java.util.stream.Stream.concat(report.detalhes().correspondencias().stream().map(FinancialMovementMatcher.Match::extrato),
                java.util.stream.Stream.concat(report.detalhes().somenteExtrato().stream(),report.detalhes().divergencias().stream().map(FinancialMovementMatcher.Difference::extrato)))
                .filter(m->m.chave().equals(p.extratoChave())).findFirst().orElseThrow(()->new IllegalArgumentException("Movimento fora do recorte atual."));
        var source=row(p.clienteId(),p.extratoChave());var meta=statementImport.decode(source).campos();
        FinancialMovementMatcher.Movement selected=null;
        if("DESVINCULAR".equals(p.acao())){
            if(!"ATIVO".equals(meta.get(PREFIX+"estado")))throw new IllegalArgumentException("Não há vínculo manual ativo para retirar.");
        }else{
            if("ATIVO".equals(meta.get(PREFIX+"estado")))throw new IllegalArgumentException("Retire o vínculo anterior antes de preparar outra conferência.");
            var all=movements(view.consultar(new FinancialViewService.Filtro(FinancialViewService.Criterio.BAIXA,FinancialViewService.Periodo.TODOS,FinancialViewService.Situacao.TODAS,p.fim(),null,null,null,null)).linhas(),java.util.Map.of(),true);
            var chosen=all.stream().filter(a->a.chave().equals(p.apiChave())).findFirst().orElseThrow(()->new IllegalArgumentException("Baixa não encontrada na empresa."));
            if(text(chosen.baixaId()).isEmpty() || all.stream().filter(a->text(a.baixaId()).equalsIgnoreCase(chosen.baixaId())).count()!=1)
                throw new IllegalArgumentException("A baixa precisa de um identificador único e auditado nesta empresa.");
            if(!chosen.data().equals(file.data()) || !chosen.tipo().equals(file.tipo()) || chosen.valor().compareTo(file.valor())!=0)
                throw new IllegalArgumentException("Data, tipo e valor precisam coincidir. Não ajuste valores para forçar um vínculo.");
            if(text(chosen.contaId()).isEmpty() || text(statementImport.decode(source).conta()).isEmpty())
                throw new IllegalArgumentException("Falta a identificação da conta na API ou o nome da conta no extrato. Confira o documento original.");
            if(!text(file.baixaId()).isEmpty()&&!text(file.baixaId()).equalsIgnoreCase(chosen.baixaId())
                || !text(file.parcelaId()).isEmpty()&&!text(file.parcelaId()).equalsIgnoreCase(text(chosen.parcelaId()))
                || !text(file.contaId()).isEmpty()&&!text(file.contaId()).equalsIgnoreCase(chosen.contaId()))
                throw new IllegalArgumentException("O identificador informado no arquivo diverge da baixa escolhida.");
            for(var other:statements.findByClienteIdOrderByIdAsc(p.clienteId()))if(!java.util.Objects.equals(other.getId(),source.getId())){
                var fields=statementImport.decode(other).campos();
                String linked="ATIVO".equals(fields.get(PREFIX+"estado"))?fields.get(PREFIX+"baixa"):null;
                if(text(chosen.baixaId()).equalsIgnoreCase(text(linked)) || text(chosen.baixaId()).equalsIgnoreCase(text(field(fields,"id da baixa","id baixa","id_baixa"))))
                    throw new IllegalArgumentException("Esta baixa já identifica outro movimento do extrato. Desfaça a conferência anterior ou revise o documento original.");
            }
            selected=accountNames(p.clienteId(),java.util.List.of(chosen)).get(0);
        }
        if(!java.util.Objects.equals(p.clienteId(),context.getClienteAtualId()))throw new IllegalArgumentException("A empresa mudou.");
        return new LinkPlan(new LinkRequest(p.clienteId(),p.inicio(),p.fim(),p.extratoChave(),p.apiChave(),p.acao(),reason),selected,file,
                selected==null?"":apiSignature(selected),fileSignature(source),rowState(source),context.getUsuarioAutenticado().getId());
    }
    @Transactional
    public void confirmarVinculo(LinkPlan expected){
        context.validarAdministrador();context.getClienteAtual(); // shared company write lock
        var current=planejarVinculo(expected.pedido());
        if(!current.assinaturaApi().equals(expected.assinaturaApi()) || !current.assinaturaExtrato().equals(expected.assinaturaExtrato())
                || !current.estado().equals(expected.estado()) || !java.util.Objects.equals(current.usuarioId(),expected.usuarioId()))
            throw new IllegalArgumentException("Os dados ou o usuário mudaram. Prepare outra conferência.");
        var source=row(expected.pedido().clienteId(),expected.pedido().extratoChave());var l=statementImport.decode(source);
        var fields=new java.util.LinkedHashMap<>(l.campos());
        var actor=context.getUsuarioAutenticado();var time=clock.instant();
        String history=fields.getOrDefault(PREFIX+"historico","");
        fields.put(PREFIX+"historico",history+(history.isBlank()?"":"\n")+time+" · "+actor.getNome()+" (#"+actor.getId()+") · "+expected.pedido().acao()+" · baixa anterior "+fields.getOrDefault(PREFIX+"baixa","não informada")+" · baixa conferida "+(current.api()==null?"não se aplica":current.api().baixaId())+" · "+expected.pedido().motivo());
        if("DESVINCULAR".equals(expected.pedido().acao()))fields.put(PREFIX+"estado","REVOGADO");
        else{
            fields.put(PREFIX+"estado","ATIVO");fields.put(PREFIX+"empresa",String.valueOf(expected.pedido().clienteId()));
            fields.put(PREFIX+"baixa",current.api().baixaId());fields.put(PREFIX+"parcela",text(current.api().parcelaId()));fields.put(PREFIX+"conta",current.api().contaId());
            fields.put(PREFIX+"assinatura api",current.assinaturaApi());fields.put(PREFIX+"assinatura extrato",current.assinaturaExtrato());
            fields.put(PREFIX+"motivo",expected.pedido().motivo());fields.put(PREFIX+"usuario",actor.getNome());fields.put(PREFIX+"horario",time.toString());
        }
        var updated=new ContaAzulStatementService.Line(l.numero(),l.movimento(),l.competencia(),l.vencimento(),l.descricao(),l.tipo(),l.origem(),l.conta(),l.situacao(),l.valor(),l.categorias(),l.centros(),l.erros(),l.rateios(),fields);
        source.registrarRevisaoHistorica(encode(updated));statements.saveAndFlush(source);
        if(!java.util.Objects.equals(expected.pedido().clienteId(),context.getClienteAtualId()))throw new IllegalArgumentException("A empresa mudou.");
    }

}
