package com.finvista.controller;

import com.finvista.service.ClienteService;
import com.finvista.service.FinancialViewService;
import com.finvista.service.FinancialViewService.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/analises/financeira")
public class FinancialViewController {

    private final FinancialViewService service;

    private final ClienteService clientes;

    public FinancialViewController(FinancialViewService service, ClienteService clientes) {
        this.service = service;
        this.clientes = clientes;
    }

    @GetMapping
    public ResponseEntity<Resposta> consultar(
            @RequestParam(defaultValue = "COMPETENCIA") Criterio criterio,
            @RequestParam(defaultValue = "MENSAL") Periodo periodo,
            @RequestParam(defaultValue = "TODAS") Situacao situacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referencia,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String centro,
            @RequestParam(defaultValue = "false") boolean consolidado,
            @RequestParam(required = false) Long usuarioAtendido) {
        try {
            var filtro = new Filtro(criterio, periodo, situacao, referencia, inicio, fim, categoria, centro);
            var resposta = consolidado ? service.consultarConsolidado(filtro, clientes, usuarioAtendido)
                    : service.consultar(filtro);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
