package com.finvista.controller;

import com.finvista.dto.ContaAzulApiSyncResponse.*;
import com.finvista.integration.contaazul.ContaAzulApiSyncService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/integracoes/conta-azul")
public class ContaAzulApiSyncController {

    private final ContaAzulApiSyncService service;

    public ContaAzulApiSyncController(ContaAzulApiSyncService service) {
        this.service = service;
    }

    @PostMapping("/parcelas/{id}/sincronizacao/previa")
    public ResponseEntity<Previa> previa(@PathVariable String id, @RequestBody Pedido pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preparar(id, pedido.clienteId()));
    }

    @PostMapping("/parcelas/{id}/sincronizacao/confirmar")
    public ResponseEntity<Resultado> confirmar(@PathVariable String id, @RequestBody Confirmacao pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.confirmar(id, pedido));
    }

    @PostMapping("/parcelas/{id}/conciliacao/previa")
    public ResponseEntity<Previa> previaVinculo(@PathVariable String id, @RequestBody PedidoVinculo pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.prepararVinculo(id, pedido.clienteId(), pedido.lancamentoId()));
    }

    @GetMapping("/sincronizacoes/{id}")
    public ResponseEntity<Auditoria> auditoria(@PathVariable UUID id, @RequestParam Long clienteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.auditoria(id, clienteId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> erro(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("mensagem", e.getReason() == null ? "Não foi possível concluir a sincronização." : e.getReason()));
    }
}
