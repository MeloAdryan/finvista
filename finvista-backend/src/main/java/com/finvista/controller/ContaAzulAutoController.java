package com.finvista.controller;

import com.finvista.dto.ContaAzulAutoResponse.*;
import com.finvista.integration.contaazul.ContaAzulAutoState;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/integracoes/conta-azul/automatica")
public class ContaAzulAutoController {

    private final ContaAzulAutoState estado;

    public ContaAzulAutoController(ContaAzulAutoState estado) {
        this.estado = estado;
    }

    @GetMapping
    public ResponseEntity<Status> status(@RequestParam Long clienteId, @RequestParam(defaultValue = "1") int paginaPendencias) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado.status(clienteId, paginaPendencias));
    }

    @PostMapping
    public ResponseEntity<Status> configurar(@RequestBody Configurar pedido) {
        estado.configurar(pedido);
        return status(pedido.clienteId(), 1);
    }

    @PostMapping("/pausar")
    public ResponseEntity<Status> pausar(@RequestBody Pedido pedido) {
        estado.pausar(pedido.clienteId());
        return status(pedido.clienteId(), 1);
    }

    @PostMapping("/executar")
    public ResponseEntity<Status> executar(@RequestBody Pedido pedido) {
        estado.solicitar(pedido.clienteId());
        return status(pedido.clienteId(), 1);
    }
}
