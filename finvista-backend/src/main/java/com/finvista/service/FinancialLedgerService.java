package com.finvista.service;

import com.fasterxml.jackson.databind.*;
import com.finvista.dto.CashFlowResponse;
import com.finvista.model.*;
import com.finvista.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialLedgerService {

    public record Movement(String id, String origem, String tipo, LocalDate data, String contaId, String conta, BigDecimal valor) {}

    public record Account(String id, String nome, BigDecimal saldoInicial, BigDecimal recebimentos, BigDecimal pagamentos,
            BigDecimal transferenciasEntrada, BigDecimal transferenciasSaida, BigDecimal ajustesAbertura, BigDecimal saldoFinal, boolean semSaldoInicial) {}

    public record Check(boolean detalhesConferidos, BigDecimal saldoContas, BigDecimal saldoMensal, boolean coberturaConfirmada) {}

    public record Result(Long clienteId, LocalDate inicio, LocalDate fim, UUID execucaoId, Instant capturadoEm,
            List<CashFlowResponse> meses, List<Account> contas, List<Movement> movimentos, boolean parcial, List<String> avisos, Check verificacao) {

        public Result(Long clienteId, LocalDate inicio, LocalDate fim, UUID execucaoId, Instant capturadoEm,
                List<CashFlowResponse> meses, List<Account> contas, List<Movement> movimentos, boolean parcial, List<String> avisos) {
            this(clienteId, inicio, fim, execucaoId, capturadoEm, meses, contas, movimentos, parcial, avisos, null);
        }
    }
    private final FinancialTransactionRepository tx;
    private final ContaAzulApiOperationRepository ops;
    private final ContaAzulSourceCaptureRepository captures;
    private final ContaAzulAutoRunRepository runs;
    private final ClienteContextService context;
    private final ObjectMapper json;
    private final Clock clock;

    public FinancialLedgerService(FinancialTransactionRepository tx, ContaAzulApiOperationRepository ops, ContaAzulSourceCaptureRepository captures, ContaAzulAutoRunRepository runs, ClienteContextService context, ObjectMapper json, Clock clock) {
        this.tx = tx;
        this.ops = ops;
        this.captures = captures;
        this.runs = runs;
        this.context = context;
        this.json = json;
        this.clock = clock;
    }

    private record Seed(LocalDate date, BigDecimal value, String name) {}

    private static BigDecimal zero() {
        return new BigDecimal("0.00");
    }

    static BigDecimal money(JsonNode n) {
        if (n == null || !n.isNumber()) {
            throw new IllegalArgumentException("Valor ausente ou inválido");

        }
        var v = n.decimalValue();
        if (v.stripTrailingZeros().scale() > 2 || v.abs().compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw new IllegalArgumentException("Valor fora do contrato");

        }
        return v.setScale(2);
    }

    static LocalDate date(JsonNode n) {
        var d = LocalDate.parse(n.asText());
        if (d.getYear() < 1900 || d.getYear() > 2200) {
            throw new IllegalArgumentException("Data fora do contrato");

        }
        return d;
    }

    private static String required(JsonNode n) {
        if (n == null || !n.isTextual() || n.asText().isBlank()) {
            throw new IllegalArgumentException("Identificador ausente");

        }
        return n.asText();
    }

    private static boolean naJanela(JsonNode data, LocalDate de, LocalDate ate) {
        try {
            var d = date(data);
            return !d.isBefore(de) && !d.isAfter(ate);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Result consultar(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio) || inicio.getYear() < 1900 || fim.getYear() > 2200) {
            throw new IllegalArgumentException("Informe um intervalo válido entre 1900 e 2200.");
        }
        Long client = context.getClienteAtualId();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
        var run = runs.findFirstByClienteIdAndSincronizacaoCompletaTrueAndStatusInOrderByConcluidoEmDesc(client, List.of("CONCLUIDA", "CONCLUIDA_COM_PENDENCIAS")).orElse(null);
        Set<String> warnings = new LinkedHashSet<>();
        List<ContaAzulAutoRun> historico = run == null ? List.of() : List.of(run);
        if (run != null && Boolean.TRUE.equals(run.incremental)) {
            var base = runs.basesHistoricas(client, run.concluidoEm, org.springframework.data.domain.PageRequest.of(0, 1));
            if (base == null || base.isEmpty()) {
                warnings.add("Captura incremental sem base histórica concluída: saldo por conta não certificado.");
            } else {
                var anteriores = runs.historicoConcluido(client, base.getContent().get(0).concluidoEm, run.concluidoEm,
                        org.springframework.data.domain.PageRequest.of(0, 1000));
                if (anteriores == null || anteriores.isEmpty() || anteriores.getTotalElements() > 1000) {
                    warnings.add("Histórico de capturas excede o limite ou está incompleto; saldo por conta não certificado.");
                } else {
                    historico = anteriores.getContent();
                    if (!historico.get(0).id.equals(base.getContent().get(0).id) || !historico.get(historico.size() - 1).id.equals(run.id)) {
                        warnings.add("Sequência das capturas históricas não foi confirmada; saldo por conta não certificado.");
                    }
                }
            }
        }
        Map<String, JsonNode> parcels = new HashMap<>();
        Map<String, Seed> seeds = new HashMap<>();
        Map<String, JsonNode> transfers = new LinkedHashMap<>();
        Set<String> conflictTransfers = new HashSet<>();
        Set<String> conflictSeeds = new HashSet<>();
        if (run == null) {
            warnings.add("Nenhuma sincronização ampla concluída: transferências e saldos iniciais não têm cobertura confirmada.");
        } else {
            for (var fonteRun : historico) {
                if (!"CONCLUIDA".equals(fonteRun.status)) {
                    warnings.add("Há execução com pendências financeiras no histórico consultado.");
                }
                var capturas = captures.findByClienteIdAndExecucaoIdOrderByTipoAscChaveAsc(client, fonteRun.id);
                if (capturas.stream().noneMatch(c -> "TRANSFERENCIA".equals(c.tipo)) || capturas.stream().noneMatch(c -> "SALDO_INICIAL".equals(c.tipo))) {
                    warnings.add("Execução sem todas as capturas auxiliares; cobertura de saldos não confirmada.");
                }
                if (Boolean.TRUE.equals(fonteRun.incremental)) {
                    var de = fonteRun.movimentosEscopoInicio;
                    var ate = fonteRun.movimentosEscopoFim;
                    boolean transferencias = capturas.stream().anyMatch(c -> "TRANSFERENCIA".equals(c.tipo));
                    boolean aberturas = capturas.stream().anyMatch(c -> "SALDO_INICIAL".equals(c.tipo));
                    if (de == null || ate == null || ate.isBefore(de) || !transferencias || !aberturas) {
                        warnings.add("Captura incremental sem janela auxiliar completa; cobertura de saldos não confirmada.");
                    } else {
                        // Complete window snapshots replace previous observations, including removals.
                        transfers.entrySet().removeIf(e -> naJanela(e.getValue().path("data"), de, ate));
                        seeds.entrySet().removeIf(e -> !e.getValue().date().isBefore(de) && !e.getValue().date().isAfter(ate));
                    }
                }
                Set<String> transferidosNestaExecucao = new HashSet<>();
                Set<String> aberturasNestaExecucao = new HashSet<>();
                for (var capture : capturas)try {
                    JsonNode raw = json.readTree(capture.conteudo);
                    if ("PARCELA".equals(capture.tipo)) {
                        parcels.put(capture.chave.toLowerCase(Locale.ROOT), raw);
                        continue;
                    }
                    if (!raw.path("itens").isArray()) {
                        throw new IllegalArgumentException();
                    }
                    for (var item : raw.path("itens"))try {
                        if ("TRANSFERENCIA".equals(capture.tipo)) {
                            String key = required(item.get("id"));
                            if (transferidosNestaExecucao.add(key)) {
                                transfers.remove(key);
                                conflictTransfers.remove(key);
                            }
                            var old = transfers.putIfAbsent(key, item);
                            if (old != null && !old.equals(item)) {
                                conflictTransfers.add(key);
                                warnings.add("Transferência repetida com conteúdo divergente: " + key);
                            }
                        } else if ("SALDO_INICIAL".equals(capture.tipo)) {
                            String account = required(item.get("id_conta_financeira"));
                            var day = date(item.path("data_competencia"));
                            var value = money(item.get("saldo_inicial"));
                            String type = required(item.get("tipo"));
                            if (!Set.of("RECEITA", "DESPESA").contains(type) || ("RECEITA".equals(type) && value.signum() < 0)) {
                                throw new IllegalArgumentException();
                            }
                            if (day.isAfter(today)) {
                                warnings.add("Saldo inicial futuro preservado fora do caixa realizado.");
                                continue;
                            }
                            value = "DESPESA".equals(type) ? value.abs().negate() : value;
                            var seed = new Seed(day, value, account);
                            if (aberturasNestaExecucao.add(account)) {
                                seeds.remove(account);
                                conflictSeeds.remove(account);
                            }
                            var old = seeds.putIfAbsent(account, seed);
                            if (old != null && !old.equals(seed)) {
                                conflictSeeds.add(account);
                                warnings.add("Mais de um saldo inicial divergente para a conta " + account + "; abertura excluída para revisão.");
                            }
                        }
                    } catch (Exception e) {
                        warnings.add("Registro auxiliar inválido na captura " + capture.chave + "; não convertido em zero.");
                    }
                } catch (Exception e) {
                    warnings.add("Captura auxiliar ilegível: " + capture.chave);
                }
            }
        }
        conflictSeeds.forEach(seeds::remove);
        seeds.entrySet().removeIf(e -> e.getValue().date().isAfter(fim));
        List<Movement> all = new ArrayList<>();
        for (var e : seeds.entrySet()) {
            all.add(new Movement("ABERTURA:" + e.getKey(), "API", "SALDO_INICIAL", e.getValue().date(), e.getKey(), e.getValue().name(), e.getValue().value()));
        }
        for (var e : transfers.entrySet()) {
            if (!conflictTransfers.contains(e.getKey()))try {
                var t = e.getValue();
                List<Movement> pair = new ArrayList<>();
                for (String side : List.of("origem", "destino")) {
                    var q = t.path(side);
                    var account = q.path("conta_financeira");
                    String accountId = required(account.get("id"));
                    var day = date(q.path("data"));
                    var value = money(q.path("composicao_valor").get("valor_liquido"));
                    if (value.signum() < 0) {
                        throw new IllegalArgumentException();
                    }
                    if (day.isAfter(today)) {
                        throw new IllegalArgumentException();
                    }
                    pair.add(new Movement("TRANSFERENCIA:" + e.getKey() + ":" + side, "API", "TRANSFERENCIA", day, accountId, account.path("nome").asText(accountId), "origem".equals(side) ? value.negate() : value));
                }
                if (pair.get(0).contaId().equals(pair.get(1).contaId())) {
                    throw new IllegalArgumentException();
                }
                all.addAll(pair);
            } catch (Exception error) {
                warnings.add("Transferência incompleta, futura ou inválida: " + e.getKey() + "; nenhum lado foi aplicado.");
            }
        }
        Map<Long, ContaAzulApiOperation> snapshots = new HashMap<>();
        Set<Long> ambiguous = new HashSet<>();
        for (var op : ops.snapshotsAtuais(client)) {
            var old = snapshots.putIfAbsent(op.getLancamentoId(), op);
            if (old != null && !Objects.equals(old.getOrigemSnapshot(), op.getOrigemSnapshot())) {
                ambiguous.add(op.getLancamentoId());

            }
        }
        for (var t : tx.findByClienteId(client)) {
            if (!Set.of("RECEITA", "DESPESA").contains(t.getTipo() == null ? "" : t.getTipo())) {
                continue;
            }
            try {
                var settlement = AuditedSettlementReader.read(t, snapshots.get(t.getId()), ambiguous.contains(t.getId()), json, today);
                JsonNode raw = parcels.get(t.getDocumentoReferencia().toLowerCase(Locale.ROOT));
                boolean same = raw != null && json.treeToValue(raw, com.finvista.integration.contaazul.ContaAzulParcelDetail.class).equals(settlement.parcela());
                for (var p : settlement.pagamentos()) {
                    JsonNode account = null;
                    if (same) {
                        for (var q : raw.path("baixas")) {
                            if (p.id().equals(q.path("id").asText())) {
                                account = q.path("conta_financeira");
                            }
                        }
                    }
                    // The audited payment carries its own account, independent of the last run's capture.
                    for (var baixa : settlement.parcela().baixas()) {
                        if (p.id().equals(baixa.id()) && baixa.conta() != null && !baixa.conta().isNull()) {
                            account = baixa.conta();
                            break;
                        }
                    }
                    String accountId = account == null ? "" : account.isTextual() ? account.asText() : account.path("id").asText("");
                    String name = account == null ? "Sem conta identificada" : account.isObject() ? account.path("nome").asText(accountId) : accountId;
                    if (name.isBlank() || name.equals(accountId)) {
                        name = "Nome da conta não informado";
                    }
                    if (accountId.isBlank()) {
                        accountId = "SEM_CONTA";
                        name = "Sem conta identificada";
                        warnings.add("Há baixas auditadas sem conta identificada na captura atual.");
                    }
                    var value = "DESPESA".equals(t.getTipo()) ? p.liquido().negate() : p.liquido();
                    all.add(new Movement("BAIXA:" + t.getDocumentoReferencia() + ":" + p.id(), "API", t.getTipo(), p.data(), accountId, name, value));
                }
            } catch (Exception e) {
                warnings.add("Há lançamentos sem baixas auditadas utilizáveis; valores não estimados.");
            }
        }
        // An opening balance establishes the baseline; older movements must not be counted again.
        long priorBaseline = all.stream().filter(m -> !"SALDO_INICIAL".equals(m.tipo()) && seeds.containsKey(m.contaId()) && m.data().isBefore(seeds.get(m.contaId()).date()) && !m.data().isBefore(inicio) && !m.data().isAfter(fim)).count();
        if (priorBaseline > 0) {
            warnings.add(priorBaseline + " movimentos anteriores à abertura da conta não foram reaplicados ao saldo; permanecem na fonte e na análise de baixas. O recorte anterior à abertura não tem saldo reconstruído.");
        }
        all.removeIf(m -> !"SALDO_INICIAL".equals(m.tipo()) && seeds.containsKey(m.contaId()) && m.data().isBefore(seeds.get(m.contaId()).date()));
        all.sort(Comparator.comparing(Movement::data).thenComparing(Movement::id));
        Map<String, List<Movement>> byAccount = new TreeMap<>();
        for (var m : all) {
            if (!m.data().isAfter(fim)) {
                byAccount.computeIfAbsent(m.contaId(), k -> new ArrayList<>()).add(m);
            }
        }
        List<Account> accounts = new ArrayList<>();
        for (var e : byAccount.entrySet()) {
            var values = e.getValue();
            var before = values.stream().filter(m -> m.data().isBefore(inicio)).map(Movement::valor).reduce(zero(), BigDecimal::add);
            BigDecimal received = zero(), paid = zero(), in = zero(), out = zero(), opening = zero();
            for (var m : values) {
                if (!m.data().isBefore(inicio)) {
                    switch (m.tipo()) {
                        case "RECEITA" ->
                            received = received.add(m.valor());
                        case "DESPESA" ->
                            paid = paid.subtract(m.valor());
                        case "SALDO_INICIAL" ->
                            opening = opening.add(m.valor());
                        default -> {
                            if (m.valor().signum() > 0) {
                                in = in.add(m.valor());
                            } else {
                                out = out.subtract(m.valor());

                            }
                        }
                    }
                }
            }
            boolean missing = !seeds.containsKey(e.getKey()) || seeds.get(e.getKey()).date().isAfter(inicio);
            if (seeds.containsKey(e.getKey()) && seeds.get(e.getKey()).date().isAfter(inicio)) {
                warnings.add("Abertura posterior ao início do recorte: o saldo anterior à abertura não está reconstruído.");
            }
            if (missing) {
                warnings.add("Há contas sem saldo inicial; os saldos são movimentos acumulados conhecidos, não saldos bancários certificados.");
            }
            accounts.add(new Account(e.getKey(), values.get(values.size() - 1).conta(), before, received, paid, in, out, opening, before.add(received).subtract(paid).add(in).subtract(out).add(opening), missing));
        }
        List<CashFlowResponse> months = new ArrayList<>();
        BigDecimal balance = all.stream().filter(m -> m.data().isBefore(inicio)).map(Movement::valor).reduce(zero(), BigDecimal::add);
        for (var month = YearMonth.from(inicio); !month.isAfter(YearMonth.from(fim)); month = month.plusMonths(1)) {
            BigDecimal in = zero(), out = zero();
            for (var m : all) {
                if (YearMonth.from(m.data()).equals(month) && !m.data().isBefore(inicio) && !m.data().isAfter(fim)) {
                    if (m.valor().signum() > 0) {
                        in = in.add(m.valor());
                    } else {
                        out = out.subtract(m.valor());

                    }
                }
            }
            BigDecimal next = balance.add(in).subtract(out);
            months.add(new CashFlowResponse(month.toString(), balance, in, out, next));
            balance = next;
        }
        if (!client.equals(context.getClienteAtualId())) {
            throw new IllegalStateException("A empresa mudou durante a consulta.");
        }
        var selectedMovements = all.stream().filter(m -> !m.data().isBefore(inicio) && !m.data().isAfter(fim)).toList();
        var verification = verify(months, accounts, selectedMovements);
        if (!verification.detalhesConferidos()) {
            warnings.add("A soma dos detalhes não reproduz os saldos ou há movimentos repetidos. Não aprovar esta conferência.");
        }
        return new Result(client, inicio, fim, run == null ? null : run.id, run == null ? null : run.concluidoEm, List.copyOf(months), List.copyOf(accounts), selectedMovements, !warnings.isEmpty(), List.copyOf(warnings), verification);
    }

    static Check verify(List<CashFlowResponse> months, List<Account> accounts, List<Movement> moves) {
        BigDecimal total = accounts.stream().map(Account::saldoFinal).reduce(zero(), BigDecimal::add);
        BigDecimal end = months.isEmpty() ? zero() : months.get(months.size() - 1).saldoFinal();
        BigDecimal start = accounts.stream().map(Account::saldoInicial).reduce(zero(), BigDecimal::add);
        boolean valid = total.compareTo(end) == 0 && (months.isEmpty() ? start.signum() == 0 : start.compareTo(months.get(0).saldoInicial()) == 0);
        Set<String> ids = new HashSet<>();
        for (var m : moves) {
            if (!ids.add(m.id())) {
                valid = false;
            }
        }
        for (var a : accounts) {
            BigDecimal formula = a.saldoInicial().add(a.recebimentos()).subtract(a.pagamentos()).add(a.transferenciasEntrada()).subtract(a.transferenciasSaida()).add(a.ajustesAbertura());
            BigDecimal detail = moves.stream().filter(m -> a.id().equals(m.contaId())).map(Movement::valor).reduce(zero(), BigDecimal::add);
            valid &= formula.compareTo(a.saldoFinal()) == 0 && a.saldoInicial().add(detail).compareTo(a.saldoFinal()) == 0;
        }
        Set<String> known = new HashSet<>();
        accounts.forEach(a -> known.add(a.id()));
        valid &= moves.stream().allMatch(m -> known.contains(m.contaId()));
        BigDecimal previous = start;
        for (var month : months) {
            BigDecimal detail = moves.stream().filter(m -> YearMonth.from(m.data()).toString().equals(month.mes())).map(Movement::valor).reduce(zero(), BigDecimal::add);
            valid &= previous.compareTo(month.saldoInicial()) == 0 && month.saldoInicial().add(month.entradas()).subtract(month.saidas()).compareTo(month.saldoFinal()) == 0
                    && month.entradas().subtract(month.saidas()).compareTo(detail) == 0;
            previous = month.saldoFinal();
        }
        return new Check(valid, total, end, false);
    }
}
