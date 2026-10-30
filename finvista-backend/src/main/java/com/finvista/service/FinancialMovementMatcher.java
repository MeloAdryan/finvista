package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class FinancialMovementMatcher {

    private FinancialMovementMatcher() {
    }

    public record Movement(String chave, String parcelaId, String baixaId, String descricao, String tipo,
            LocalDate data, BigDecimal valor, String contaId) {

        public Movement(String chave, String parcelaId, String baixaId, String descricao, String tipo, LocalDate data, BigDecimal valor) {
            this(chave, parcelaId, baixaId, descricao, tipo, data, valor, null);
        }
    }

    public record Match(String criterio, Movement api, Movement extrato, boolean contaConferida) {

        public Match(String criterio, Movement api, Movement extrato) {
            this(criterio, api, extrato, false);
        }
    }

    public record Ambiguity(Movement extrato, List<Movement> candidatos) {}

    public record Difference(Movement api, Movement extrato, List<String> campos, BigDecimal diferencaValor) {}

    public record Result(List<Match> correspondencias, List<Movement> somenteApi, List<Movement> somenteExtrato,
            List<Ambiguity> ambiguidades, List<Difference> divergencias) {

        public Result(List<Match> correspondencias, List<Movement> somenteApi, List<Movement> somenteExtrato, List<Ambiguity> ambiguidades) {
            this(correspondencias, somenteApi, somenteExtrato, ambiguidades, List.of());
        }
    }

    private static String normalized(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean known(String id) {
        return !normalized(id).isEmpty();
    }

    private static boolean equalId(String a, String b) {
        return known(a) && known(b) && normalized(a).equals(normalized(b));
    }

    private static List<String> differences(Movement a, Movement b) {
        var fields = new ArrayList<String>();
        if (!normalized(a.tipo()).equals(normalized(b.tipo()))) {
            fields.add("Tipo");
        }
        if (!Objects.equals(a.data(), b.data())) {
            fields.add("Data");
        }
        if (a.valor().compareTo(b.valor()) != 0) {
            fields.add("Valor líquido");
        }
        if (known(b.parcelaId()) && !equalId(a.parcelaId(), b.parcelaId())) {
            fields.add("Parcela");
        }
        if (known(a.contaId()) && known(b.contaId()) && !equalId(a.contaId(), b.contaId())) {
            fields.add("Conta financeira");
        }
        return List.copyOf(fields);
    }

    private static boolean suggestion(Movement a, Movement b) {
        return differences(a, b).isEmpty();
    }

    private static void validate(List<Movement> rows, String source) {
        if (rows == null) {
            throw new IllegalArgumentException("Lista ausente na fonte " + source);
        }
        Set<String> keys = new HashSet<>();
        for (var r : rows) {
            if (r == null || !known(r.chave()) || !keys.add(r.chave()) || r.data() == null || r.valor() == null
                    || r.valor().stripTrailingZeros().scale() > 2
                    || !Set.of("receita", "despesa").contains(normalized(r.tipo()))) {
                throw new IllegalArgumentException("Movimento inválido ou chave repetida na fonte " + source);
            }
        }
    }

    public static Result compare(List<Movement> api, List<Movement> file) {
        validate(api, "API");
        validate(file, "extrato");
        var matches = new ArrayList<Match>();
        var ambiguous = new ArrayList<Ambiguity>();
        var conflicts = new ArrayList<Difference>();
        Set<String> usedA = new HashSet<>(), usedF = new HashSet<>(), reservedA = new HashSet<>();
        Map<String, List<Movement>> byApiId = new HashMap<>(), byFileId = new HashMap<>();
        for (var a : api) {
            if (known(a.baixaId())) {
                byApiId.computeIfAbsent(normalized(a.baixaId()), k -> new ArrayList<>()).add(a);
            }
        }
        for (var f : file) {
            if (known(f.baixaId())) {
                byFileId.computeIfAbsent(normalized(f.baixaId()), k -> new ArrayList<>()).add(f);
            }
        }
        for (var f : file) {
            if (!known(f.baixaId())) {
                continue;
            }
            var candidates = byApiId.getOrDefault(normalized(f.baixaId()), List.of());
            candidates.forEach(a -> reservedA.add(a.chave()));
            if (candidates.size() == 1 && byFileId.get(normalized(f.baixaId())).size() == 1) {
                var a = candidates.get(0);
                var fields = differences(a, f);
                if (fields.isEmpty()) {
                    matches.add(new Match("ID_BAIXA_DATA_TIPO_VALOR", a, f, equalId(a.contaId(), f.contaId()))); 
                }else {
                    conflicts.add(new Difference(a, f, fields, a.valor().subtract(f.valor())));
                }
                usedA.add(a.chave());
                usedF.add(f.chave());
            } else if (!candidates.isEmpty()) {
                ambiguous.add(new Ambiguity(f, List.copyOf(candidates)));
            }
        }
        // No explicit-ID conflict can be consumed again as a suggestion for another row.
        for (var f : file) {
            if (known(f.baixaId()) || usedF.contains(f.chave())) {
                continue;
            }
            var candidates = api.stream().filter(a -> !usedA.contains(a.chave()) && !reservedA.contains(a.chave()) && suggestion(a, f)).toList();
            if (candidates.size() == 1) {
                var a = candidates.get(0);
                long reverse = file.stream().filter(x -> !known(x.baixaId()) && !usedF.contains(x.chave()) && suggestion(a, x)).count();
                if (reverse == 1) {
                    matches.add(new Match("SUGESTAO_DATA_TIPO_VALOR_SEM_VINCULO_CERTIFICADO", a, f, equalId(a.contaId(), f.contaId())));
                    usedA.add(a.chave());
                    usedF.add(f.chave());
                } else {
                    ambiguous.add(new Ambiguity(f, List.copyOf(candidates)));
                }
            } else if (candidates.size() > 1) {
                ambiguous.add(new Ambiguity(f, List.copyOf(candidates)));
            }
        }
        return new Result(List.copyOf(matches), api.stream().filter(a -> !usedA.contains(a.chave())).toList(),
                file.stream().filter(f -> !usedF.contains(f.chave())).toList(), List.copyOf(ambiguous), List.copyOf(conflicts));
    }
}
