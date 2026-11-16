package com.finvista.controller;

import com.finvista.service.FinancialViewService.*;
import com.finvista.service.GroupPlanningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analises/grupo")
public class GroupPlanningController {
    private final GroupPlanningService grupos;

    public GroupPlanningController(GroupPlanningService grupos) {
        this.grupos = grupos;
    }

    @GetMapping("/orcamentos")
    public ResponseEntity<?> orcamentos(
            @RequestParam(required = false) Long usuarioAtendido,
            @RequestParam(defaultValue = "COMPETENCIA") Criterio criterio,
            @RequestParam(defaultValue = "MENSAL") Periodo periodo,
            @RequestParam(defaultValue = "TODAS") Situacao situacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referencia,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        var filtro = new Filtro(criterio, periodo, situacao, referencia, inicio, fim, null, null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grupos.orcamentos(usuarioAtendido, filtro));
    }

    @GetMapping("/projecao")
    public ResponseEntity<?> projecao(
            @RequestParam(required = false) Long usuarioAtendido,
            @RequestParam(required = false) BigDecimal receitaMensal,
            @RequestParam(required = false) BigDecimal saldoInicial,
            @RequestParam(required = false) BigDecimal variacaoPercentual) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(grupos.projetar(usuarioAtendido, receitaMensal, saldoInicial, variacaoPercentual));
    }

    @GetMapping("/conferencia")
    public ResponseEntity<?> conferencia(
            @RequestParam(required = false) Long usuarioAtendido,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grupos.conferir(usuarioAtendido, inicio, fim));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> parametroInvalido() {
        return ResponseEntity.badRequest().build();
    }
}
