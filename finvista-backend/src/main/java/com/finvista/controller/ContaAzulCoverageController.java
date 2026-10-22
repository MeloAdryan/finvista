package com.finvista.controller;

import com.finvista.integration.contaazul.*;
import com.finvista.repository.FinancialTransactionRepository;
import com.finvista.service.ClienteContextService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Consulta somente leitura. A paginação e as ausências não autorizam gravação.
 */
@RestController
@RequestMapping("/api/integracoes/conta-azul/cobertura-competencia")
public class ContaAzulCoverageController {

    private final ContaAzulFinancialClient api;
    private final ContaAzulConnectionService connection;
    private final FinancialTransactionRepository transactions;
    private final ClienteContextService context;

    public ContaAzulCoverageController(ContaAzulFinancialClient api, ContaAzulConnectionService connection,
            FinancialTransactionRepository transactions, ClienteContextService context) {
        this.api = api;
        this.connection = connection;
        this.transactions = transactions;
        this.context = context;
    }

    public record ParcelaResumo(String id, String descricao, LocalDate competencia, LocalDate vencimento) {}

    public record Item(ParcelaResumo parcela, boolean vinculoApiLocal, boolean competenciaInformada) {}

    public record Consulta(Long clienteId, String criterio, LocalDate inicio, LocalDate fim, LocalDate vencimentoInicio, LocalDate vencimentoFim, int pagina, int totalItens, int tamanhoPagina,
            Instant consultadoEm, List<Item> itens, String aviso) {}

    @GetMapping
    public ResponseEntity<Consulta> consultar(@RequestParam Long clienteId, @RequestParam ContaAzulFinancialClient.Tipo tipo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoFim, @RequestParam(defaultValue = "1") int pagina) {
        context.validarAdministrador();
        if (!Objects.equals(clienteId, context.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou.");
        }
        if (inicio == null || fim == null || fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) > 365 || pagina < 1 || pagina > 10000 || vencimentoInicio == null || vencimentoFim == null || vencimentoFim.isBefore(vencimentoInicio) || ChronoUnit.DAYS.between(vencimentoInicio, vencimentoFim) > 365) {
            throw new IllegalArgumentException("Escolha um intervalo de competência de até 366 dias e página válida.");
        }
        var fonte = api.consultarPorCompetencia(connection.acessoParaConsulta(clienteId), tipo, inicio, fim, vencimentoInicio, vencimentoFim, pagina);
        Set<String> ids = new HashSet<>();
        for (var t : transactions.findByClienteId(clienteId)) {
            if ("CONTA_AZUL_API".equals(t.getOrigem()) && t.getDocumentoReferencia() != null) {
                ids.add(t.getDocumentoReferencia().toLowerCase(Locale.ROOT));
            }
        }
        var itens = fonte.itens().stream().map(p -> new Item(new ParcelaResumo(p.id(), p.descricao(), p.competencia(), p.vencimento()), ids.contains(p.id().toLowerCase(Locale.ROOT)), p.competencia() != null)).toList();
        if (!Objects.equals(clienteId, context.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou durante a consulta.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Consulta(clienteId, "COMPETENCIA", inicio, fim, vencimentoInicio, vencimentoFim, pagina, fonte.totalItens(), ContaAzulFinancialClient.TAMANHO,
                Instant.now(), itens, "Esta consulta cruza competência e vencimento. Repita para outras janelas de vencimentos necessárias. Consulte todas as páginas e os dois tipos; vínculo existente não comprova valores atualizados. Ausência de vínculo exige conferência de duplicidade antes de incluir. A autorização da automação continua por vencimento."));
    }
}
