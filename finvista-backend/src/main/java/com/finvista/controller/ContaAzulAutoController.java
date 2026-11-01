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

    private static final String LINK_SESSION="FINVISTA_CONFERENCIA_VINCULO";
    private record LinkPending(String token,java.time.Instant expires,com.finvista.service.ContaAzulReconciliationService.LinkPlan plan) {}
    public record LinkPreview(String token,java.time.Instant validade,com.finvista.service.FinancialMovementMatcher.Movement api,
            com.finvista.service.FinancialMovementMatcher.Movement extrato,String acao,String motivo,Long clienteId) {}
    public record LinkConfirmation(String token,Long clienteId,boolean confirmado) {}
    @PostMapping("/conferencia/planejar-vinculo")
    public ResponseEntity<LinkPreview> planejarVinculo(@RequestBody com.finvista.service.ContaAzulReconciliationService.LinkRequest request,jakarta.servlet.http.HttpSession session){
        session.removeAttribute(LINK_SESSION);
        var p=reconciliacao.planejarVinculo(request);var pending=new LinkPending(java.util.UUID.randomUUID().toString(),java.time.Instant.now().plusSeconds(600),p);
        session.setAttribute(LINK_SESSION,pending);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new LinkPreview(pending.token(),pending.expires(),p.api(),p.extrato(),p.pedido().acao(),p.pedido().motivo(),p.pedido().clienteId()));
    }
    @PostMapping("/conferencia/confirmar-vinculo")
    public ResponseEntity<java.util.Map<String,String>> confirmarVinculo(@RequestBody LinkConfirmation request,jakarta.servlet.http.HttpSession session){
        var saved=session.getAttribute(LINK_SESSION);session.removeAttribute(LINK_SESSION);
        if(!(saved instanceof LinkPending p)||!request.confirmado()||!java.util.Objects.equals(request.token(),p.token())
                ||!p.expires().isAfter(java.time.Instant.now())||!java.util.Objects.equals(request.clienteId(),p.plan().pedido().clienteId()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Conferência expirada ou confirmação ausente. Prepare outra prévia.");
        reconciliacao.confirmarVinculo(p.plan());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("mensagem","Conferência registrada. Valores e fontes preservados. Consulte o recorte novamente."));
    }

}
