package com.finvista.service;

import com.fasterxml.jackson.databind.*;
import com.finvista.model.*;
import com.finvista.repository.ContaAzulApiOperationRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class ContaAzulOriginGuard {

    private final ContaAzulApiOperationRepository operacoes;
    private final ObjectMapper json;

    public ContaAzulOriginGuard(ContaAzulApiOperationRepository operacoes, ObjectMapper json) {
        this.operacoes = operacoes;
        this.json = json;
    }

    public record Identidade(String tipo, String descricao, LocalDate competencia, LocalDate vencimento,
            BigDecimal valor, String referencia) {}

    public Map<Long, List<Identidade>> historico(Long cliente) {
        Map<Long, List<Identidade>> resultado = new TreeMap<>();
        for (var op : operacoes.findByClienteIdAndAcaoOrderByConcluidoEmAsc(cliente, "VINCULAR_EXCEL")) {
            try {
                JsonNode n = json.readTree(op.getAntes());
                if (n == null || !"CONTA_AZUL_EXCEL".equals(n.path("origem").asText())) {
                    throw new IllegalArgumentException();
                }
                var valor = n.get("valorOriginal");
                if (valor == null || valor.isNull()) {
                    valor = n.get("valor");
                }
                var competencia = data(n.get("dataCompetencia"));
                if (competencia == null) {
                    competencia = data(n.get("data"));
                }
                var identidade = new Identidade(texto(n.path("tipo").asText()), texto(n.path("descricao").asText()),
                        competencia, data(n.get("dataVencimento")), new BigDecimal(valor.asText()), texto(n.path("documentoReferencia").asText()));
                resultado.computeIfAbsent(op.getLancamentoId(), id -> new ArrayList<>()).add(identidade);
            } catch (Exception e) {
                throw new IllegalStateException("Não foi possível conferir o histórico Excel/API. Nenhuma linha deve ser importada antes de revisar a auditoria.");
            }
        }
        return resultado;
    }

    public boolean proteger(FinancialTransaction entrada, FinancialTransaction api, Map<Long, List<Identidade>> historico) {
        Identidade fonte = identidade(entrada);
        if (possivel(fonte, identidade(api))) {
            return true;
        }
        return historico.getOrDefault(api.getId(), List.of()).stream().anyMatch(h -> possivel(fonte, h));
    }

    private Identidade identidade(FinancialTransaction t) {
        return new Identidade(texto(t.getTipo()), texto(t.getDescricao()),
                t.getDataCompetencia() == null ? t.getData() : t.getDataCompetencia(), t.getDataVencimento(),
                t.getValorOriginal() == null ? t.getValor() : t.getValorOriginal(), texto(t.getDocumentoReferencia()));
    }

    private boolean possivel(Identidade a, Identidade b) {
        if (a.tipo().isEmpty() || !a.tipo().equals(b.tipo())) {
            return false;
        }
        boolean referencia = !a.referencia().isEmpty() && a.referencia().equals(b.referencia());
        boolean descricao = !a.descricao().isEmpty() && a.descricao().equals(b.descricao());
        boolean valor = a.valor() != null && b.valor() != null && a.valor().compareTo(b.valor()) == 0;
        boolean data = (a.competencia() != null && a.competencia().equals(b.competencia()))
                || (a.vencimento() != null && a.vencimento().equals(b.vencimento()));
        return (descricao && (data || valor)) || (valor && data) || (referencia && (data || valor || descricao));
    }

    private String texto(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDate data(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isArray() && n.size() == 3) {
            return LocalDate.of(n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt());
        }
        return LocalDate.parse(n.asText());
    }
}
