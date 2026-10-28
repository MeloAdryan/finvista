package com.finvista.controller;

import com.finvista.integration.contaazul.*;
import com.finvista.service.ClienteContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integracoes/conta-azul/diagnostico-movimentos")
public class ContaAzulMovementDiagnosticController {

    private final ClienteContextService context;
    private final ContaAzulAutoAccess access;
    private final ContaAzulMovementClient movements;

    public ContaAzulMovementDiagnosticController(ClienteContextService context, ContaAzulAutoAccess access, ContaAzulMovementClient movements) {
        this.context = context;
        this.access = access;
        this.movements = movements;
    }

    public record Resultado(Long clienteId, LocalDate inicio, LocalDate fim, String fonte, boolean consultaAceita,
            Integer totalInformado, Integer itensNestaPagina, Integer httpOriginal, String classificacao,
            List<String> parametrosMencionados, List<Integer> limitesEmDias, List<String> tiposDeErro) {}

    @GetMapping
    public ResponseEntity<Resultado> consultar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        if (inicio == null || fim == null || inicio.getYear() < 1900 || fim.getYear() > 2200 || fim.isBefore(inicio)
                || ChronoUnit.DAYS.between(inicio, fim) > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha no máximo 31 dias para esta consulta de diagnóstico.");
        }
        Long cliente = context.getClienteAtualId();
        try {
            var pagina = movements.consultar(access.acesso(cliente), "TRANSFERENCIA", inicio, fim, 1);
            return response(new Resultado(cliente, inicio, fim, "TRANSFERENCIA", true, pagina.total(), pagina.itens().size(), 200, "CONSULTA_ACEITA", List.of(), List.of(), List.of()));
        } catch (Exception error) {
            Integer http = null;
            String mensagem = "";
            var tipos = new ArrayList<String>();
            var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
            for (Throwable t = error; t != null && tipos.size() < 8 && seen.add(t); t = t.getCause()) {
                tipos.add(t.getClass().getName());
                if (t instanceof ResponseStatusException status && http == null) {
                    http = status.getStatusCode().value();
                }
                if (t instanceof RestClientResponseException original) {
                    http = original.getStatusCode().value();
                    String raw = original.getResponseBodyAsString();
                    if (raw.length() <= 4096) {
                        try {
                            var json = new ObjectMapper().readTree(raw);
                            var field = json.path("error");
                            if (field.isTextual()) {
                                mensagem = field.asText();
                        
                            }} catch (Exception ignored) {
                            /* Never expose unstructured content. */ }
                    }
                }
            }
            String normal = Normalizer.normalize(mensagem, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            var campos = new ArrayList<String>();
            for (String campo : List.of("data_inicio", "data_fim", "tamanho_pagina", "pagina", "ids_conta_financeira")) {
                if (normal.contains(campo)) {
                    campos.add(campo);
                }
            }
            var limites = new ArrayList<Integer>();
            var matcher = Pattern.compile("(?<![0-9])([0-9]{1,3})\\s+dias?\\b").matcher(normal);
            while (matcher.find() && limites.size() < 4) {
                int n = Integer.parseInt(matcher.group(1));
                if (n > 0 && !limites.contains(n)) {
                    limites.add(n);
            
                }}
            String categoria = "VALIDACAO_NAO_IDENTIFICADA";
            if (normal.contains("periodo") || normal.contains("intervalo")) {
                categoria = "VALIDACAO_PERIODO"; 
            }else if (normal.contains("data")) {
                categoria = "VALIDACAO_DATA"; 
            }else if (normal.contains("pagina")) {
                categoria = "VALIDACAO_PAGINACAO";
            }
            if (Objects.equals(http, 401) || Objects.equals(http, 403)) {
                categoria = "ACESSO_RECUSADO"; 
            }else if (Objects.equals(http, 429)) {
                categoria = "LIMITE_DE_CONSULTAS"; 
            }else if (http == null) {
                categoria = "FALHA_SEM_STATUS_HTTP";
            }
            return response(new Resultado(cliente, inicio, fim, "TRANSFERENCIA", false, null, null, http, categoria, List.copyOf(campos), List.copyOf(limites), List.copyOf(tipos)));
        }
    }

    private ResponseEntity<Resultado> response(Resultado r) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r);
    }
}
