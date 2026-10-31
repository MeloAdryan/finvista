package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.integration.contaazul.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialViewService {

    public enum Criterio {
        COMPETENCIA, VENCIMENTO, BAIXA, BAIXA_EXTRATO
    }

    public enum Periodo {
        SEMANAL, MENSAL, ANUAL, TODOS, PERSONALIZADO
    }

    public enum Situacao {
        TODAS, PAGAS, ABERTAS, VENCIDAS, PARCIAIS
    }

    public record Filtro(Criterio criterio, Periodo periodo, Situacao situacao,
            LocalDate referencia, LocalDate inicio, LocalDate fim, String categoria, String centro) {}

    public record Faixa(LocalDate inicio, LocalDate fim) {}

    public record Linha(Long id, String documento, String descricao, String tipo, LocalDate data,
            LocalDate competencia, LocalDate vencimento, String categoria, String centro, BigDecimal valor,
            BigDecimal principalOriginal, BigDecimal principalRealizado, BigDecimal principalAberto,
            BigDecimal liquidoRealizado, BigDecimal liquidoAberto, String movimentoId, String contaId) {

        public Linha(Long id, String documento, String descricao, String tipo, LocalDate data, LocalDate competencia,
                LocalDate vencimento, String categoria, String centro, BigDecimal valor, BigDecimal principalOriginal,
                BigDecimal principalRealizado, BigDecimal principalAberto, BigDecimal liquidoRealizado, BigDecimal liquidoAberto, String movimentoId) {
            this(id, documento, descricao, tipo, data, competencia, vencimento, categoria, centro, valor, principalOriginal,
                    principalRealizado, principalAberto, liquidoRealizado, liquidoAberto, movimentoId, null);
        }

        public Linha(Long id, String documento, String descricao, String tipo, LocalDate data, LocalDate competencia, LocalDate vencimento, String categoria, String centro, BigDecimal valor, BigDecimal principalOriginal, BigDecimal principalRealizado, BigDecimal principalAberto, BigDecimal liquidoRealizado, BigDecimal liquidoAberto) {
            this(id, documento, descricao, tipo, data, competencia, vencimento, categoria, centro, valor, principalOriginal, principalRealizado, principalAberto, liquidoRealizado, liquidoAberto, null);
        }

        public Linha(Long id, String documento, String descricao, String tipo, LocalDate data,
                LocalDate competencia, LocalDate vencimento, String categoria, String centro, BigDecimal valor) {
            this(id, documento, descricao, tipo, data, competencia, vencimento, categoria, centro, valor,
                    null, null, null, null, null);
        }
    }

    public record Total(BigDecimal receita, BigDecimal despesa, BigDecimal resultado) {}

    public record Serie(String periodo, BigDecimal receita, BigDecimal despesa, BigDecimal resultado) {}

    public record Limite(Long id, String nome, LocalDate inicio, LocalDate fim, BigDecimal planejado,
            BigDecimal consumoPeriodo, BigDecimal percentual, boolean recorteParcial) {}

    public record Resposta(Long clienteId, Filtro filtro, Faixa intervalo, LocalDate hoje,
            Total totais, List<Serie> serie, List<Linha> linhas, Map<String, Integer> exclusoes,
            List<String> categorias, List<String> centros, List<Limite> metas, List<Limite> orcamentos,
            String baseValor, String aviso) {}

    private record Movimento(FinancialTransaction origem, LocalDate data, BigDecimal principal, BigDecimal valor, String movimentoId, String contaId) {

        Movimento(FinancialTransaction origem, LocalDate data, BigDecimal principal, BigDecimal valor) {
            this(origem, data, principal, valor, null, null);
        }
    }

    private final FinancialTransactionRepository transactions;
    private final ContaAzulApiOperationRepository operations;
    private final ClienteContextService context;
    private final ExpenseAllocationService allocations;
    private final SpendingGoalService goals;
    private final BudgetPipelineService budgets;
    private final ObjectMapper json;
    private final Clock clock;
    private final StatementMovementRepository statements;
    private final StatementImportService statementImport;

    public FinancialViewService(FinancialTransactionRepository transactions, ContaAzulApiOperationRepository operations,
            ClienteContextService context, ExpenseAllocationService allocations, SpendingGoalService goals,
            BudgetPipelineService budgets, ObjectMapper json, Clock clock,
            StatementMovementRepository statements, StatementImportService statementImport) {
        this.transactions = transactions;
        this.operations = operations;
        this.context = context;
        this.allocations = allocations;
        this.goals = goals;
        this.budgets = budgets;
        this.json = json;
        this.clock = clock;
        this.statements = statements;
        this.statementImport = statementImport;
    }

    public static Faixa intervalo(Filtro f, LocalDate hoje, List<LocalDate> datas) {
        LocalDate ref = f.referencia() == null ? hoje : f.referencia();
        if (ref.getYear() < 1900 || ref.getYear() > 2200) {
            throw new IllegalArgumentException("Referência fora do intervalo 1900–2200.");
        }
        return switch (f.periodo()) {
            case SEMANAL -> {
                LocalDate de = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Faixa(de, de.plusDays(6));
            }
            case MENSAL ->
                new Faixa(ref.withDayOfMonth(1), YearMonth.from(ref).atEndOfMonth());
            case ANUAL ->
                new Faixa(LocalDate.of(ref.getYear(), 1, 1), LocalDate.of(ref.getYear(), 12, 31));
            case TODOS ->
                new Faixa(datas.stream().min(LocalDate::compareTo).orElse(ref),
                datas.stream().max(LocalDate::compareTo).orElse(ref));
            case PERSONALIZADO -> {
                if (f.inicio() == null || f.fim() == null || f.fim().isBefore(f.inicio())
                        || f.inicio().getYear() < 1900 || f.fim().getYear() > 2200) {
                    throw new IllegalArgumentException("Informe datas válidas entre 1900 e 2200, em ordem crescente.");
                }
                yield new Faixa(f.inicio(), f.fim());
            }
        };
    }

    @Transactional(readOnly = true)
    public Resposta consultar(Filtro f) {
        Objects.requireNonNull(f.criterio());
        Objects.requireNonNull(f.periodo());
        Objects.requireNonNull(f.situacao());
        if ((f.criterio() == Criterio.BAIXA || f.criterio() == Criterio.BAIXA_EXTRATO) && f.situacao() != Situacao.TODAS && f.situacao() != Situacao.PAGAS) {
            throw new IllegalArgumentException("Baixa consulta valores pagos/recebidos. Para contas abertas ou vencidas, use vencimento.");
        }
        if ((f.categoria() != null && f.categoria().length() > 255) || (f.centro() != null && f.centro().length() > 255)) {
            throw new IllegalArgumentException("Filtro muito longo.");
        }
        Long cliente = context.getClienteAtualId();
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        if (f.criterio() == Criterio.BAIXA_EXTRATO) {
            return consultarExtrato(cliente, f, hoje);
        }
        var registros = transactions.findByClienteId(cliente);
        Map<String, Integer> excluidos = new TreeMap<>();
        Map<Long, ContaAzulApiOperation> snapshots = new HashMap<>();
        Set<Long> ambiguos = new HashSet<>();
        if (f.criterio() == Criterio.BAIXA) {
            for (var op : operations.snapshotsAtuais(cliente)) {
                var anterior = snapshots.putIfAbsent(op.getLancamentoId(), op);
                if (anterior != null && !Objects.equals(anterior.getOrigemSnapshot(), op.getOrigemSnapshot())) {
                    ambiguos.add(op.getLancamentoId());
                }
            }
        }
        List<Movimento> movimentos = new ArrayList<>();
        for (var t : registros) {
            if (!Set.of("RECEITA", "DESPESA").contains(tipo(t))) {
                continue;
            }
            if (!FinancialStatusPolicy.ativo(t) && f.criterio() != Criterio.BAIXA) {
                contar(excluidos, "Cancelados");
                continue;
            }
            if (f.criterio() == Criterio.BAIXA) {
                try {
                    var audited = AuditedSettlementReader.read(t, snapshots.get(t.getId()), ambiguos.contains(t.getId()), json, hoje);
                    for (var b : audited.pagamentos()) {
                        movimentos.add(new Movimento(t, b.data(), b.principal(), b.liquido(), b.id(), b.contaId()));
                    }
                } catch (Exception e) {
                    contar(excluidos, "Sem baixas auditadas utilizáveis");
                }
                continue;
            }
            if (!correspondeSituacao(t, f.situacao(), hoje)) {
                if (f.situacao() != Situacao.TODAS && (t.getValorTotalAberto() == null || t.getValorTotalRealizado() == null)) {
                    contar(excluidos, "Sem valores suficientes para a situação");
                }
                continue;
            }
            LocalDate data = f.criterio() == Criterio.COMPETENCIA ? (t.getDataCompetencia() == null ? t.getData() : t.getDataCompetencia()) : t.getDataVencimento();
            if (data == null) {
                contar(excluidos, "Sem data para o critério");
                continue;
            }
            if (data.getYear() < 1900 || data.getYear() > 2200) {
                contar(excluidos, "Data fora de 1900–2200");
                continue;
            }
            if (f.criterio() == Criterio.COMPETENCIA && "RENEGOCIADO".equals(t.getSituacao())) {
                contar(excluidos, "Renegociação: competência depende de vínculo conferido com o novo evento");
                continue;
            }
            BigDecimal valor = t.getValor();
            if (f.criterio() == Criterio.VENCIMENTO) {
                if (t.getValorTotalRealizado() == null || t.getValorTotalAberto() == null) {
                    contar(excluidos, "Sem líquido pago e aberto informado");
                    continue;
                }
                valor = t.getValorTotalRealizado().add(t.getValorTotalAberto());
            }
            if (valor == null || t.getValor() == null) {
                contar(excluidos, "Sem valor principal");
                continue;
            }
            movimentos.add(new Movimento(t, data, t.getValor(), valor));
        }
        Faixa faixa = intervalo(f, hoje, movimentos.stream().map(Movimento::data).toList());
        Map<Long, List<ExpenseAllocationService.Parte>> partes = new HashMap<>();
        var partesConferidas = f.criterio() == Criterio.BAIXA && registros.stream().anyMatch(t -> FinancialStatusPolicy.cancelado(t.getSituacao()))
                ? allocations.dividirDetalhadoParaBaixas(cliente, registros) : allocations.dividirDetalhado(cliente, registros);
        for (var p : partesConferidas) {
            partes.computeIfAbsent(p.lancamento().getId(), k -> new ArrayList<>()).add(p.parte());
        }
        List<Linha> todas = new ArrayList<>();
        for (var m : movimentos) {
            if (m.data().isBefore(faixa.inicio()) || m.data().isAfter(faixa.fim())) {
                continue;
            }
            var t = m.origem();
            var ps = partes.getOrDefault(t.getId(), List.of(new ExpenseAllocationService.Parte(FinancialStatusPolicy.cancelado(t.getSituacao()) ? "Categoria a revisar" : "Sem categoria", FinancialStatusPolicy.cancelado(t.getSituacao()) ? "Centro a revisar" : "Sem centro de custo", t.getValor())));
            var valores = distribuir(m.principal(), t.getValor(), ps.stream().map(ExpenseAllocationService.Parte::valor).toList());
            for (int i = 0; i < ps.size(); i++) {
                todas.add(linha(m, ps.get(i).categoria(), ps.get(i).centroCusto(), valores.get(i)));
            }
            BigDecimal ajuste = m.valor().subtract(m.principal());
            if (ajuste.signum() != 0) {
                todas.add(linha(m, "Ajustes financeiros (juros, multa, desconto e taxa)", "Sem centro de custo", ajuste));
            }
        }
        var categorias = todas.stream().map(Linha::categoria).distinct().sorted().toList();
        var centros = todas.stream().map(Linha::centro).distinct().sorted().toList();
        var linhas = todas.stream().filter(l -> filtro(f.categoria(), l.categoria()) && filtro(f.centro(), l.centro()))
                .sorted(Comparator.comparing(Linha::data).thenComparing(Linha::id, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        List<Limite> metas = new ArrayList<>(), orcamentos = new ArrayList<>();
        for (var g : goals.listarComSituacao()) {
            if (cruza(g.dataInicio(), g.dataFim(), faixa)) {
                metas.add(limite(g.id(), g.serie() == null ? "Meta #" + g.id() : g.serie().nome(), g.dataInicio(), g.dataFim(), g.valorLimite(), linhas, g.categoria(), g.centroCusto(), faixa));
            }
        }
        for (var b : budgets.listar()) {
            if (cruza(b.dataInicio(), b.dataFim(), faixa)) {
                orcamentos.add(limite(b.id(), b.nome(), b.dataInicio(), b.dataFim(), b.valorPlanejado(), linhas, b.categoria(), b.centroCusto(), faixa));
            }
        }
        if (!cliente.equals(context.getClienteAtualId())) {
            throw new IllegalStateException("A empresa selecionada mudou.");
        }
        return new Resposta(cliente, f, faixa, hoje, total(linhas), series(linhas, faixa, f.periodo()), linhas, excluidos, categorias, centros, metas, orcamentos,
                f.criterio() == Criterio.COMPETENCIA ? "PRINCIPAL" : "LIQUIDO",
                "Cobertura da base importada. Exclusões referem-se à base inteira, antes do recorte. Situação avaliada na data de hoje. Vencimento não comprova pagamento. Ajustes financeiros ficam separados dos rateios originais. Limites não são proporcionalizados; consumo usa a interseção com a vigência. Orçamentos sobrepostos podem compartilhar despesas.");
    }

    /**
     * Separate source: never adds extract movements to the same API payment.
     */
    private Resposta consultarExtrato(Long cliente, Filtro f, LocalDate hoje) {
        List<Linha> base = new ArrayList<>();
        Map<String, Integer> exclusions = new TreeMap<>();
        for (var saved : statements.findByClienteIdOrderByIdAsc(cliente)) {
            var l = statementImport.decode(saved);
            String exclusion = StatementCashPolicy.exclusion(l, hoje);
            if (exclusion != null) {
                contar(exclusions, exclusion);
                continue;
            }
            String tipo = ContaAzulStatementService.normalize(l.tipo()).toUpperCase(Locale.ROOT);
            var parts = l.rateios();
            boolean usable = parts != null && !parts.isEmpty() && parts.stream().allMatch(p -> p.valor() != null);
            if (usable) {
                usable = parts.stream().map(ContaAzulStatementService.Allocation::valor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(l.valor()) == 0;
            }
            if (!usable) {
                base.add(new Linha(saved.getId(), "EXTRATO:" + saved.getChave() + ":" + saved.getOcorrencia(), l.descricao(), tipo,
                        l.movimento(), l.competencia(), l.vencimento(), "Categoria a revisar", "Sem centro de custo",
                        "DESPESA".equals(tipo) ? l.valor().negate() : l.valor()));
                contar(exclusions, "Rateios incompletos ou divergentes: valor mantido em Categoria a revisar");
                continue;
            }
            for (var part : parts) {
                String center = part.centro() != null && !part.centro().isBlank() && part.valorCentro() != null
                        && part.valorCentro().compareTo(part.valor()) == 0 ? part.centro() : "Sem centro de custo";
                base.add(new Linha(saved.getId(), "EXTRATO:" + saved.getChave() + ":" + saved.getOcorrencia(), l.descricao(), tipo,
                        l.movimento(), l.competencia(), l.vencimento(), part.categoria(), center,
                        "DESPESA".equals(tipo) ? part.valor().negate() : part.valor()));
            }
        }
        Faixa range = intervalo(f, hoje, base.stream().map(Linha::data).toList());
        var within = base.stream().filter(l -> !l.data().isBefore(range.inicio()) && !l.data().isAfter(range.fim())).toList();
        var categories = within.stream().map(Linha::categoria).distinct().sorted().toList();
        var centers = within.stream().map(Linha::centro).distinct().sorted().toList();
        var lines = within.stream().filter(l -> filtro(f.categoria(), l.categoria()) && filtro(f.centro(), l.centro()))
                .sorted(Comparator.comparing(Linha::data).thenComparing(Linha::id)).toList();
        List<Limite> goalLimits = new ArrayList<>(), budgetLimits = new ArrayList<>();
        for (var g : goals.listarComSituacao()) {
            if (cruza(g.dataInicio(), g.dataFim(), range)) {
                goalLimits.add(limite(g.id(), g.serie() == null ? "Meta #" + g.id() : g.serie().nome(), g.dataInicio(), g.dataFim(), g.valorLimite(), lines, g.categoria(), g.centroCusto(), range));
            }
        }
        for (var b : budgets.listar()) {
            if (cruza(b.dataInicio(), b.dataFim(), range)) {
                budgetLimits.add(limite(b.id(), b.nome(), b.dataInicio(), b.dataFim(), b.valorPlanejado(), lines, b.categoria(), b.centroCusto(), range));
            }
        }
        if (!cliente.equals(context.getClienteAtualId())) {
            throw new IllegalStateException("A empresa selecionada mudou.");
        }
        return new Resposta(cliente, f, range, hoje, total(lines), series(lines, range, f.periodo()), lines, exclusions, categories, centers, goalLimits, budgetLimits, "LIQUIDO",
                "Fonte exclusiva: extratos confirmados desta empresa. Não soma parcelas ou baixas da API. Data de movimento e valor líquido; transferências e origens não financeiras ficam fora dos indicadores. Cobertura limitada aos arquivos enviados. Rateios não conferidos permanecem sinalizados para revisão. Limites não são proporcionalizados.");
    }

    public static List<BigDecimal> distribuir(BigDecimal valor, BigDecimal original, List<BigDecimal> pesos) {
        List<BigDecimal> resultado = new ArrayList<>();
        BigDecimal usado = BigDecimal.ZERO;
        if (pesos.isEmpty()) {
            throw new IllegalArgumentException("Rateio vazio.");
        }
        for (int i = 0; i < pesos.size(); i++) {
            BigDecimal parte = i == pesos.size() - 1 ? valor.subtract(usado) : original.signum() == 0 ? BigDecimal.ZERO
                    : valor.multiply(pesos.get(i)).divide(original, 2, RoundingMode.HALF_UP);
            resultado.add(parte);
            usado = usado.add(parte);
        }
        return List.copyOf(resultado);
    }

    static boolean correspondeSituacao(FinancialTransaction t, Situacao s, LocalDate hoje) {
        if (s == Situacao.TODAS) {
            return true;
        }
        var aberto = t.getValorTotalAberto();
        var pago = t.getValorTotalRealizado();
        if (aberto == null || pago == null) {
            return false;
        }
        return switch (s) {
            case PAGAS ->
                aberto.signum() == 0 && pago.signum() > 0;
            case ABERTAS ->
                aberto.signum() > 0;
            case VENCIDAS ->
                aberto.signum() > 0 && t.getDataVencimento() != null && t.getDataVencimento().isBefore(hoje);
            case PARCIAIS ->
                aberto.signum() > 0 && pago.signum() > 0;
            default ->
                true;
        };
    }

    private static boolean igual(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static void contar(Map<String, Integer> m, String k) {
        m.merge(k, 1, Integer::sum);
    }

    private static String tipo(FinancialTransaction t) {
        return t.getTipo() == null ? "" : t.getTipo().trim().toUpperCase(Locale.ROOT);
    }

    private static boolean filtro(String f, String v) {
        return f == null || f.isBlank() || f.trim().equalsIgnoreCase(v);
    }

    private static Linha linha(Movimento m, String categoria, String centro, BigDecimal valor) {
        var t = m.origem();
        return new Linha(t.getId(), t.getDocumentoReferencia(), t.getDescricao(), tipo(t), m.data(), t.getDataCompetencia(), t.getDataVencimento(), categoria, centro, valor,
                t.getValor(), t.getValorRealizado(), t.getValorAberto(), t.getValorTotalRealizado(), t.getValorTotalAberto(), m.movimentoId(), m.contaId());
    }

    private static boolean cruza(LocalDate a, LocalDate b, Faixa f) {
        return !a.isAfter(f.fim()) && !b.isBefore(f.inicio());
    }

    private static Limite limite(Long id, String nome, LocalDate a, LocalDate b, BigDecimal limite, List<Linha> linhas, String cat, String centro, Faixa faixa) {
        BigDecimal consumo = linhas.stream().filter(l -> "DESPESA".equals(l.tipo()) && !l.data().isBefore(a) && !l.data().isAfter(b) && filtro(cat, l.categoria()) && filtro(centro, l.centro())).map(Linha::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Limite(id, nome, a, b, limite, consumo, limite.signum() > 0 ? consumo.multiply(new BigDecimal("100")).divide(limite, 2, RoundingMode.HALF_UP) : null, faixa.inicio().isAfter(a) || faixa.fim().isBefore(b));
    }

    public static Total total(List<Linha> linhas) {
        BigDecimal r = BigDecimal.ZERO, d = BigDecimal.ZERO;
        for (var l : linhas) {
            if ("RECEITA".equals(l.tipo())) {
                r = r.add(l.valor());
            } else if ("DESPESA".equals(l.tipo())) {
                d = d.add(l.valor());
            }
        }
        return new Total(r, d, r.subtract(d));
    }

    static List<Serie> series(List<Linha> linhas, Faixa f, Periodo periodo) {
        boolean diario = java.time.temporal.ChronoUnit.DAYS.between(f.inicio(), f.fim()) < 32;
        Map<String, List<Linha>> grupos = new TreeMap<>();
        for (var l : linhas) {
            grupos.computeIfAbsent(diario ? l.data().toString() : YearMonth.from(l.data()).toString(), k -> new ArrayList<>()).add(l);
        }
        if (diario) {
            for (var d = f.inicio(); !d.isAfter(f.fim()); d = d.plusDays(1)) {
                grupos.putIfAbsent(d.toString(), List.of());
            }
        } else {
            for (var m = YearMonth.from(f.inicio()); !m.isAfter(YearMonth.from(f.fim())); m = m.plusMonths(1)) {
                grupos.putIfAbsent(m.toString(), List.of());
            }
        }
        return grupos.entrySet().stream().map(e -> {
            var t = total(e.getValue());
            return new Serie(e.getKey(), t.receita(), t.despesa(), t.resultado());
        }).toList();
    }
}
