package com.finvista.controller;

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

    public FinancialViewController(FinancialViewService service) {
        this.service = service;
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
            @RequestParam(required = false) String centro) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(new Filtro(criterio, periodo, situacao, referencia, inicio, fim, categoria, centro)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
