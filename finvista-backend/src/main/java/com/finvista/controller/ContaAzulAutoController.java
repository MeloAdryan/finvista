package com.finvista.controller;

import com.finvista.dto.ContaAzulAutoResponse.*;
import com.finvista.integration.contaazul.ContaAzulAutoState;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/integracoes/conta-azul/automatica")
public class ContaAzulAutoController {

    private final ContaAzulAutoState estado;
    @org.springframework.beans.factory.annotation.Autowired
    private com.finvista.service.ContaAzulReconciliationService reconciliacao;

    public ContaAzulAutoController(ContaAzulAutoState estado) {
        this.estado = estado;
    }

    @GetMapping
    public ResponseEntity<Status> status(@RequestParam Long clienteId, @RequestParam(defaultValue = "1") int paginaPendencias) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado.status(clienteId, paginaPendencias));
    }

    @GetMapping("/conferencia")
    public ResponseEntity<com.finvista.service.ContaAzulReconciliationService.Resultado> conferencia(@RequestParam Long clienteId,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate inicio,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fim){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reconciliacao.conferir(clienteId,inicio,fim));
    }

    @GetMapping("/execucoes")
    public ResponseEntity<Historico> historico(@RequestParam Long clienteId, @RequestParam(defaultValue = "1") int pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado.historico(clienteId, pagina));
    }

    @GetMapping("/execucoes/{id}/pendencias")
    public ResponseEntity<Revisao> revisar(@RequestParam Long clienteId, @PathVariable java.util.UUID id, @RequestParam(defaultValue = "1") int pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado.revisar(clienteId, id, pagina));
    }

    @GetMapping("/execucoes/{id}/fontes")
    public ResponseEntity<ContaAzulAutoState.Fontes> fontes(@RequestParam Long clienteId,@PathVariable java.util.UUID id,
            @RequestParam(defaultValue="PARCELA") String tipo,@RequestParam(defaultValue="1") int pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado.fontes(clienteId,id,tipo,pagina));
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
