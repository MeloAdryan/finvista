package com.finvista.controller;

import com.finvista.service.ContaAzulOriginReviewService;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integracoes/conta-azul/revisao-origem")
public class ContaAzulOriginReviewController {
    private static final String KEY="FINVISTA_REVISAO_ORIGEM";
    private final ContaAzulOriginReviewService service;
    private final Clock clock;
    private record Pending(String token,Instant expires,ContaAzulOriginReviewService.Plan plan) {}
    public record Preview(String token,Instant validade,Long clienteId,String parcelaId,String descricao,
            java.math.BigDecimal principal,java.math.BigDecimal aberto,String situacao,String motivo,String substitutaId) {}
    public record Confirm(String token,Long clienteId,String parcelaId,boolean confirmado) {}
    public ContaAzulOriginReviewController(ContaAzulOriginReviewService service,Clock clock){this.service=service;this.clock=clock;}
    @PostMapping("/planejar")
    public ResponseEntity<Preview> plan(@RequestBody ContaAzulOriginReviewService.Request request,HttpSession session) {
        session.removeAttribute(KEY);
        var p=service.plan(request);
        var pending=new Pending(UUID.randomUUID().toString(),clock.instant().plusSeconds(600),p);
        session.setAttribute(KEY,pending);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Preview(pending.token(),pending.expires(),p.clienteId(),p.parcelaId(),p.descricao(),p.principal(),p.aberto(),p.situacao(),p.motivo(),p.substitutaId()));
    }
    @PostMapping("/confirmar")
    public Map<String,String> confirm(@RequestBody Confirm request,HttpSession session) {
        var saved=session.getAttribute(KEY);session.removeAttribute(KEY);
        if(!(saved instanceof Pending p) || !request.confirmado() || !Objects.equals(request.token(),p.token())
                || !p.expires().isAfter(clock.instant()) || !Objects.equals(request.clienteId(),p.plan().clienteId())
                || !Objects.equals(request.parcelaId(),p.plan().parcelaId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Confirmação ausente ou revisão expirada. Prepare outra revisão.");
        service.apply(p.plan());
        return Map.of("mensagem","Remoção na origem confirmada. Parcela cancelada no FinVista com histórico preservado. Atualize os indicadores. Pendências antigas continuam no histórico da automação.");
    }
}
