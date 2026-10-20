package com.finvista.service;

import com.finvista.dto.FinancialAccountsResponse;
import com.finvista.integration.contaazul.ContaAzulAccountsException;
import com.finvista.integration.contaazul.ContaAzulAccountsClient;
import com.finvista.integration.contaazul.ContaAzulAutoAccess;
import com.finvista.repository.ContaAzulConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class FinancialAccountsService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FinancialAccountsService.class);
    private static final Set<String> TIPOS = Set.of("CONTA_CORRENTE", "CAIXINHA", "POUPANCA", "APLICACAO", "INVESTIMENTO");

    private record Cache(FinancialAccountsResponse resposta, Instant expira) {}
    private final Map<Long, Cache> cache = new LinkedHashMap<>();
    private final Object[] travas = java.util.stream.IntStream.range(0, 32).mapToObj(i -> new Object()).toArray();
    private final ClienteContextService contexto;
    private final ContaAzulConnectionRepository conexoes;
    private final ContaAzulAutoAccess acesso;
    private final ContaAzulAccountsClient api;
    private final Clock clock;

    public FinancialAccountsService(ClienteContextService contexto, ContaAzulConnectionRepository conexoes,
            ContaAzulAutoAccess acesso, ContaAzulAccountsClient api, Clock clock) {
        this.contexto = contexto;
        this.conexoes = conexoes;
        this.acesso = acesso;
        this.api = api;
        this.clock = clock;
    }

    public FinancialAccountsResponse consultar() {
        Long id = contexto.getClienteAtualId();
        synchronized (travas[Math.floorMod(id.hashCode(), travas.length)]) {
            if (!conexoes.existsById(id)) {
                synchronized (cache) {
                    cache.remove(id);
                }
                return new FinancialAccountsResponse(id, "NAO_CONECTADO", null, null, false,
                        "A empresa precisa de uma conexão autorizada com a Conta Azul.", 0, List.of());
            }
            Cache anterior;
            synchronized (cache) {
                anterior = cache.get(id);
            }
            if (anterior != null && anterior.expira().isAfter(clock.instant())) {
                return anterior.resposta();
            }
            FinancialAccountsResponse resultado;
            String etapa = "ACESSO";
            try {
                long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                String token = acesso.acesso(id);
                etapa = "LISTAGEM";
                var todas = api.listar(token);
                var selecionadas = todas.stream().filter(c -> TIPOS.contains(c.tipo())).toList();
                if (selecionadas.size() > 100) {
                    throw new ContaAzulAccountsException("LIMITE_CONTAS", "SALDO", null);
                }
                var contas = new ArrayList<FinancialAccountsResponse.Conta>();
                BigDecimal total = BigDecimal.ZERO;
                etapa = "SALDO";
                for (var conta : selecionadas) {
                    if (System.nanoTime() > limite) {
                        throw new ContaAzulAccountsException("TEMPO_CONSULTA", "SALDO", null);
                    }
                    // Espaça chamadas para respeitar o limite de requisições por segundo da API.
                    Thread.sleep(150);
                    BigDecimal saldo = api.saldo(token, conta.id());
                    total = total.add(saldo);
                    contas.add(new FinancialAccountsResponse.Conta(conta.id(), conta.nome(), conta.tipo(), saldo));
                }
                contas.sort(Comparator.comparing(FinancialAccountsResponse.Conta::nome, String.CASE_INSENSITIVE_ORDER));
                resultado = new FinancialAccountsResponse(id, contas.isEmpty() ? "SEM_CONTAS" : "DISPONIVEL", total,
                        clock.instant(), false, contas.isEmpty() ? "Nenhuma conta de banco, caixa ou aplicação ativa encontrada."
                        : "Saldo informado pela Conta Azul para bancos, caixa e aplicações ativos.", todas.size() - contas.size(), List.copyOf(contas));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                var diagnostico = diagnosticar(e, etapa);
                String mensagem = mensagem(diagnostico);
                // Nunca registra corpo de erro externo, tokens, mensagem da exceção ou stack trace.
                log.warn("Consulta de saldos: cliente={}, codigo={}, etapa={}, http={}", id,
                        diagnostico.codigo(), diagnostico.etapa(), diagnostico.httpStatus());
                var r = anterior == null ? null : anterior.resposta();
                resultado = r != null && r.saldo() != null
                        ? new FinancialAccountsResponse(id, r.estado(), r.saldo(), r.consultadoEm(), true, mensagem, r.contasExcluidas(), r.contas(), diagnostico)
                        : new FinancialAccountsResponse(id, "INDISPONIVEL", null, null, false, mensagem, 0, List.of(), diagnostico);
            }
            long segundos = resultado.desatualizado() || resultado.saldo() == null ? 30 : 120;
            synchronized (cache) {
                if (cache.size() >= 256) {
                    cache.remove(cache.keySet().iterator().next());
                }
                cache.put(id, new Cache(resultado, clock.instant().plusSeconds(segundos)));
            }
            return resultado;
        }
    }

    private static FinancialAccountsResponse.Diagnostico diagnosticar(Exception e, String etapa) {
        if (e instanceof ContaAzulAccountsException f) {
            return new FinancialAccountsResponse.Diagnostico(f.codigo(), f.etapa(), f.httpStatus());
        }
        if (e instanceof RestClientResponseException r) {
            return new FinancialAccountsResponse.Diagnostico("HTTP_EXTERNO", etapa, r.getStatusCode().value());
        }
        return new FinancialAccountsResponse.Diagnostico(etapa.equals("ACESSO") ? "ACESSO_NAO_RENOVADO" : "FALHA_CONSULTA", etapa, null);
    }

    private static String mensagem(FinancialAccountsResponse.Diagnostico d) {
        if (d.httpStatus() != null) {
            if (d.httpStatus() == 401) {
                return "A Conta Azul recusou a autorização desta consulta. Renove o acesso ou autorize novamente em Importar Dados.";
            }
            if (d.httpStatus() == 403) {
                return "A Conta Azul não permitiu consultar as contas ou os saldos. Confira a autorização e as permissões da integração.";
            }
            if (d.httpStatus() == 429) {
                return "Limite de consultas atingido. Aguarde antes de atualizar.";
            }
            if (d.httpStatus() == 400) {
                return "A Conta Azul recusou os parâmetros da consulta. Consulte os detalhes abaixo para revisar a integração.";
            }
            if (d.httpStatus() == 404) {
                return "Uma conta ou consulta não foi encontrada na Conta Azul. Atualize novamente após conferir as contas cadastradas.";
            }
            return "A Conta Azul não concluiu a consulta. Aguarde e tente novamente.";
        }
        return switch (d.codigo()) {
            case "ACESSO_NAO_RENOVADO" ->
                "Não foi possível obter ou renovar o acesso. Confira a configuração e a autorização em Importar Dados.";
            case "REDE_OU_TEMPO", "TEMPO_CONSULTA" ->
                "A conexão com a Conta Azul demorou ou falhou. Aguarde e tente novamente.";
            case "LISTA_INCOMPLETA", "LISTAGEM_MUDOU" ->
                "A listagem de contas ficou incompleta ou mudou durante a consulta. Atualize novamente.";
            case "CONTA_INVALIDA", "RESPOSTA_INVALIDA" ->
                "A Conta Azul retornou dados que a integração não conseguiu validar. Consulte os detalhes abaixo.";
            case "SALDO_AUSENTE" ->
                "Uma conta não retornou saldo. O novo total não foi calculado para evitar uma soma parcial.";
            case "LIMITE_CONTAS" ->
                "Esta empresa ultrapassa o limite de contas por consulta desta integração. O total não foi calculado.";
            default ->
                "Não foi possível concluir a consulta dos saldos. Consulte os detalhes abaixo.";
        };
    }
}
