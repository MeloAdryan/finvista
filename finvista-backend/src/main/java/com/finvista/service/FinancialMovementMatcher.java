package com.finvista.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Read-only, one-to-one reconciliation. Similar totals never constitute
 * identity.
 */
public final class FinancialMovementMatcher {

    private FinancialMovementMatcher() {
    }

    public record Movement(String chave, String parcelaId, String baixaId, String descricao, String tipo, LocalDate data, BigDecimal valor) {}

    public record Match(String criterio, Movement api, Movement extrato) {}

    public record Ambiguity(Movement extrato, List<Movement> candidatos) {}

    public record Result(List<Match> correspondencias, List<Movement> somenteApi, List<Movement> somenteExtrato, List<Ambiguity> ambiguidades) {}

    private static boolean same(Movement a, Movement b) {
        return Objects.equals(a.tipo(), b.tipo()) && Objects.equals(a.data(), b.data()) && a.valor().compareTo(b.valor()) == 0;
    }

    private static boolean known(String id) {
        return id != null && !id.isBlank();
    }

    private static boolean identity(Movement a, Movement b) {
        return known(a.baixaId()) && known(b.baixaId()) && a.baixaId().equalsIgnoreCase(b.baixaId()) && (!known(b.parcelaId()) || Objects.equals(a.parcelaId(), b.parcelaId()));
    }

    public static Result compare(List<Movement> api, List<Movement> file) {
        List<Match> matches = new ArrayList<>();
        Set<String> usedA = new HashSet<>(), usedF = new HashSet<>();
        List<Ambiguity> ambiguous = new ArrayList<>();
        Set<String> blocked = new HashSet<>();
        for (var f : file) {
            if (known(f.baixaId())) {
                var candidates = api.stream().filter(a -> identity(a, f)).toList();
                if (candidates.size() == 1 && same(candidates.get(0), f) && file.stream().filter(x -> identity(candidates.get(0), x)).count() == 1) {
                    var a = candidates.get(0);
                    matches.add(new Match("ID_BAIXA_DATA_TIPO_VALOR", a, f));
                    usedA.add(a.chave());
                    usedF.add(f.chave());
                } else {
                    blocked.add(f.chave());
                    if (!candidates.isEmpty()) {
                        ambiguous.add(new Ambiguity(f, candidates));
                
                    }}
            }
        }
        // Suggestions require reciprocal uniqueness, so duplicates cannot be paired arbitrarily.
        for (var f : file) {
            if (!usedF.contains(f.chave()) && !blocked.contains(f.chave())) {
                var candidates = api.stream().filter(a -> !usedA.contains(a.chave()) && same(a, f)).toList();
                if (candidates.size() == 1) {
                    var a = candidates.get(0);
                    long reverse = file.stream().filter(x -> !usedF.contains(x.chave()) && !blocked.contains(x.chave()) && same(a, x)).count();
                    if (reverse == 1) {
                        matches.add(new Match("SUGESTAO_DATA_TIPO_VALOR_SEM_VINCULO_CERTIFICADO", a, f));
                        usedA.add(a.chave());
                        usedF.add(f.chave());
                    } else {
                        ambiguous.add(new Ambiguity(f, candidates));
                    }
                } else if (candidates.size() > 1) {
                    ambiguous.add(new Ambiguity(f, candidates));
                }
            }
        }
        return new Result(List.copyOf(matches), api.stream().filter(a -> !usedA.contains(a.chave())).toList(), file.stream().filter(f -> !usedF.contains(f.chave())).toList(), List.copyOf(ambiguous));
    }
}
