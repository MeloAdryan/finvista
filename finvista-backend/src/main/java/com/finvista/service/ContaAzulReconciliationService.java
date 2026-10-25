package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaAzulReconciliationService {

    private final ClienteContextService context;
    private final FinancialViewService view;
    @org.springframework.beans.factory.annotation.Autowired private com.finvista.repository.StatementMovementRepository statements;
    @org.springframework.beans.factory.annotation.Autowired private StatementImportService statementImport;

    public ContaAzulReconciliationService(ClienteContextService context, FinancialViewService view) {
        this.context = context;
        this.view = view;
    }

    public record Resultado(Long clienteId, LocalDate inicio, LocalDate fim, String situacao,
            FinancialViewService.Total api, FinancialViewService.Total extrato, BigDecimal diferencaReceita,
            BigDecimal diferencaDespesa, int linhasApi, int linhasExtrato, Map<String, Integer> exclusoesApi,
            Map<String, Integer> exclusoesExtrato, String aviso, FinancialMovementMatcher.Result detalhes) {}

    @Transactional(readOnly = true)
    public Resultado conferir(Long clienteId, LocalDate inicio, LocalDate fim) {
        context.validarAdministrador();
        return conferirLeitura(clienteId,inicio,fim);
    }

    @Transactional(readOnly = true)
    public Resultado conferirLeitura(Long clienteId,LocalDate inicio,LocalDate fim) {
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
        var fields=new java.util.HashMap<String,java.util.Map<String,String>>();
        if(statements!=null&&statementImport!=null)for(var row:statements.findByClienteIdOrderByIdAsc(clienteId))fields.put("EXTRATO:"+row.getChave()+":"+row.getOcorrencia(),statementImport.decode(row).campos());
        var details=FinancialMovementMatcher.compare(movements(api.linhas(),java.util.Map.of(),true),movements(file.linhas(),fields,false));
        return new Resultado(clienteId, inicio, fim, status, api.totais(), file.totais(), receita, despesa, api.linhas().size(), file.linhas().size(), api.exclusoes(), file.exclusoes(),
                "Conferência de data de baixa/movimento e líquido das receitas/despesas disponíveis, com transferências fora desses indicadores. Igualdade dos totais não comprova correspondência linha a linha nem cobertura integral. Arquivos filtrados e exclusões podem produzir diferenças. As exclusões se referem à base inteira da fonte, antes do recorte. Nenhum movimento foi somado ou vinculado entre fontes. As sugestões por data/tipo/valor não certificam identidade; ambiguidades exigem revisão.", details);
    }

    private static java.util.List<FinancialMovementMatcher.Movement> movements(java.util.List<FinancialViewService.Linha> lines,java.util.Map<String,java.util.Map<String,String>> fields,boolean api){
        var groups=new java.util.LinkedHashMap<String,java.util.List<FinancialViewService.Linha>>();
        for(var l:lines){var key=l.id()+":"+l.documento()+":"+l.movimentoId()+":"+l.data()+":"+l.tipo();groups.computeIfAbsent(key,k->new java.util.ArrayList<>()).add(l);}
        var result=new java.util.ArrayList<FinancialMovementMatcher.Movement>();
        for(var e:groups.entrySet()){
            var l=e.getValue().get(0);var f=fields.getOrDefault(l.documento(),java.util.Map.of());
            String parcel=api?l.documento():field(f,"id da parcela","id parcela","id_parcela");
            String payment=api?l.movimentoId():field(f,"id da baixa","id baixa","id_baixa");
            var amount=e.getValue().stream().map(FinancialViewService.Linha::valor).reduce(BigDecimal.ZERO,BigDecimal::add);
            result.add(new FinancialMovementMatcher.Movement(e.getKey(),parcel,payment,l.descricao(),l.tipo(),l.data(),amount));
        }
        return result;
    }
    private static String field(java.util.Map<String,String> fields,String...names){for(String name:names){var value=fields.get(name);if(value!=null&&!value.isBlank())return value.trim();}return null;}

    private static FinancialViewService.Filtro filtro(FinancialViewService.Criterio tipo, LocalDate inicio, LocalDate fim) {
        return new FinancialViewService.Filtro(tipo, FinancialViewService.Periodo.PERSONALIZADO, FinancialViewService.Situacao.TODAS, fim, inicio, fim, null, null);
    }
}
