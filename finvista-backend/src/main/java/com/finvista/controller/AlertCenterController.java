package com.finvista.controller;

import com.finvista.dto.AlertCenterResponse;
import com.finvista.dto.AlertCenterResponse.*;
import com.finvista.dto.FinancialAlertsResponse.Gravidade;
import com.finvista.dto.FinancialAlertsResponse.Tipo;
import com.finvista.model.FinancialAlertRecord.Estado;
import com.finvista.service.AlertCenterService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/alertas-financeiros/central")
public class AlertCenterController {
    private final AlertCenterService service;
    public AlertCenterController(AlertCenterService service) { this.service = service; }

    @GetMapping
    public AlertCenterResponse listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Gravidade gravidade,
            @RequestParam(required = false) Tipo tipo,
            @RequestParam(required = false) Estado estado,
            @RequestParam(required = false) Boolean lido,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(inicio, fim, gravidade, tipo, estado, lido, pagina, tamanho);
    }

    @PostMapping("/reavaliar")
    public Atualizacao atualizar(@RequestBody(required = false) Periodo periodo) {
        return service.atualizar(periodo == null ? null : periodo.inicio(), periodo == null ? null : periodo.fim());
    }

    @PostMapping("/{id}/lido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLido(@PathVariable Long id) { service.marcarLido(id); }
}
