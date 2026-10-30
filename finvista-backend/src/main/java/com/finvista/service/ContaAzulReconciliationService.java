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
            int divergencias, int ambiguidades, int contasNaoComparaveis, boolean totaisReproduzidos) {}

    public record Cobertura(String fonte, String ultimaSituacao, java.time.Instant ultimoTermino,
            Integer pendenciasEmpresa, boolean consultaAoVivo, boolean coberturaConfirmada) {}

    public record Resultado(Long clienteId, LocalDate inicio, LocalDate fim, String situacao,
            FinancialViewService.Total api, FinancialViewService.Total extrato, BigDecimal diferencaReceita,
            BigDecimal diferencaDespesa, int linhasApi, int linhasExtrato, Map<String, Integer> exclusoesApi,
            Map<String, Integer> exclusoesExtrato, String aviso, FinancialMovementMatcher.Result detalhes, Resumo resumo, Cobertura cobertura) {}

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
        var apiMovements = movements(api.linhas(), java.util.Map.of(), true);
        var fileMovements = movements(file.linhas(), fields, false);
        var details = FinancialMovementMatcher.compare(apiMovements, fileMovements);
        boolean integrity = totalsMatch(apiMovements, api.totais()) && totalsMatch(fileMovements, file.totais());
        int byId = (int) details.correspondencias().stream().filter(m -> m.criterio().equals("ID_BAIXA_DATA_TIPO_VALOR")).count();
        int suggestions = details.correspondencias().size() - byId;
        int unknownAccounts = (int) details.correspondencias().stream().filter(m -> !m.contaConferida()).count();
        if ("TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS".equals(status) && (!details.divergencias().isEmpty() || !details.ambiguidades().isEmpty()
                || !details.somenteApi().isEmpty() || !details.somenteExtrato().isEmpty())) {
            status = "DIVERGENCIA_DE_REGISTROS";
        }
        if ("TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS".equals(status) && suggestions == 0 && integrity) {
            status = "VALORES_POR_ID_CONFERIDOS_COBERTURA_NAO_CERTIFICADA";
        }
        var summary = new Resumo(apiMovements.size(), fileMovements.size(), byId, suggestions, details.divergencias().size(), details.ambiguidades().size(), unknownAccounts, integrity);
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
                "Conferência de data de baixa/movimento e líquido das receitas/despesas disponíveis, com transferências fora desses indicadores. Igualdade dos totais não comprova correspondência linha a linha nem cobertura integral. Arquivos filtrados e exclusões podem produzir diferenças. As exclusões se referem à base inteira da fonte, antes do recorte. Nenhum movimento foi somado ou vinculado entre fontes. As sugestões por data/tipo/valor não certificam identidade; ambiguidades exigem revisão. Divergências por ID mostram os campos afetados. Contas sem ID nos dois lados não são certificadas. Pendências referem-se à empresa inteira. Datas fora do recorte podem aparecer somente em uma fonte; amplie o período para investigar.", details, summary, coverage);
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
            result.add(new FinancialMovementMatcher.Movement(e.getKey(), parcel, payment, l.descricao(), l.tipo(), l.data(), amount, account));
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
}
