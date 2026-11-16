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
@RequestMapping("/api/analises/financeira/consolidado")
public class ConsolidatedDashboardController {
    private final FinancialViewService financeiro;
    private final ClienteService clientes;

    public ConsolidatedDashboardController(FinancialViewService financeiro, ClienteService clientes) {
        this.financeiro = financeiro;
        this.clientes = clientes;
    }

    @GetMapping
    public ResponseEntity<Consolidado> consultar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam Criterio criterio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        if (inicio.isAfter(fim) || inicio.getYear() < 1900 || fim.getYear() > 2200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalo inválido.");
        }
        var filtro = new Filtro(criterio, Periodo.PERSONALIZADO, Situacao.TODAS,
                null, inicio, fim, null, null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(financeiro.consolidar(filtro, clientes, usuarioId));
    }
}