package com.finvista.controller;

import com.finvista.integration.contaazul.ContaAzulConnectionService;
import com.finvista.integration.contaazul.ContaAzulFinancialClient;
import com.finvista.service.ClienteContextService;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integracoes/conta-azul")
public class ContaAzulFinancialController {

    public record Consulta(Long clienteId, ContaAzulFinancialClient.Tipo tipo,
            LocalDate inicio, LocalDate fim, int pagina, int tamanhoPagina, int totalItens,
            Instant consultadoEm, List<ContaAzulFinancialClient.Parcela> itens) {}
    private final ContaAzulConnectionService conexoes;
    private final ContaAzulFinancialClient financeiro;
    private final ClienteContextService contexto;

    public ContaAzulFinancialController(ContaAzulConnectionService conexoes, ContaAzulFinancialClient financeiro, ClienteContextService contexto) {
        this.conexoes = conexoes;
        this.financeiro = financeiro;
        this.contexto = contexto;
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<java.util.Map<String, String>> falhaConsulta(org.springframework.web.server.ResponseStatusException erro) {
        return ResponseEntity.status(erro.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("mensagem", erro.getReason() == null ? "Não foi possível concluir a consulta." : erro.getReason()));
    }

    @GetMapping("/financeiro")
    public ResponseEntity<Consulta> consultar(
            @RequestParam(name = "clienteId") Long clienteId,
            @RequestParam(name = "tipo") ContaAzulFinancialClient.Tipo tipo,
            @RequestParam(name = "inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(name = "fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(name = "pagina", defaultValue = "1") int pagina) {
        contexto.validarAdministrador();
        if (clienteId == null || clienteId <= 0 || tipo == null || inicio == null || fim == null || inicio.isAfter(fim) || ChronoUnit.DAYS.between(inicio, fim) > 365 || pagina < 1 || pagina > 10000) {
            throw new IllegalArgumentException("Informe empresa, tipo e intervalo de vencimentos de até 366 dias, com página válida.");
        }
        String acesso = conexoes.acessoParaConsulta(clienteId);
        var p = financeiro.consultar(acesso, tipo, inicio, fim, pagina);
        if (!clienteId.equals(contexto.getClienteAtualId())) {
            throw new IllegalArgumentException("A empresa selecionada mudou durante a consulta. Atualize a tela.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Consulta(clienteId, tipo, inicio, fim, pagina, ContaAzulFinancialClient.TAMANHO, p.totalItens(), Instant.now(), p.itens()));
    }
}
