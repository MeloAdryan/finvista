package com.finvista.controller;
import com.finvista.service.*;
import com.finvista.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController
@RequestMapping("/api/analises/sincronizacao")
public class FinancialSyncHealthController {
    public record Saude(Long clienteId,Instant ultimaSincronizacao,Long idadeSegundos,boolean atrasada,
        long pendencias,String conferencia,Instant conferidoEm,String conferenciaMensal,boolean conectada,boolean identidadeConfirmada,String empresaExternaId) {}
    private final ClienteContextService ctx;private final ClienteService clientes;
    private final ContaAzulAutoRunRepository runs;private final ContaAzulAutoItemRepository itens;
    private final ContaAzulConnectionRepository conexoes;private final Clock clock;
    public FinancialSyncHealthController(ClienteContextService ctx,ClienteService clientes,ContaAzulAutoRunRepository runs,
            ContaAzulAutoItemRepository itens,ContaAzulConnectionRepository conexoes,Clock clock) {
        this.ctx=ctx;this.clientes=clientes;this.runs=runs;this.itens=itens;this.conexoes=conexoes;this.clock=clock;
    }
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public ResponseEntity<List<Saude>> consultar(@RequestParam(defaultValue="false") boolean consolidado,
            @RequestParam(required=false) Long usuarioAtendido) {
        List<Long> ids=consolidado ? (usuarioAtendido==null?clientes.listarEmpresasVinculadas():clientes.listarEmpresasDoCliente(usuarioAtendido))
            .stream().map(c->c.id()).distinct().toList():List.of(ctx.getClienteAtualId());
        var saida=new ArrayList<Saude>();
        for(Long id:ids) {
            var c=conexoes.findById(id).orElse(null);
            var r=runs.findFirstByClienteIdAndStatusInOrderByConcluidoEmDesc(id,List.of("CONCLUIDA","CONCLUIDA_COM_PENDENCIAS")).orElse(null);
            Instant ultima=r==null?null:r.concluidoEm;
            Long idade=ultima==null?null:Math.max(0,Duration.between(ultima,clock.instant()).getSeconds());
            // Latest state per parcel; counts are not accumulated again on every run.
            long pendentes=itens.countPendenciasAtuais(id);
            saida.add(new Saude(id,ultima,idade,c!=null&&(idade==null||idade>86400),pendentes,
                r==null||r.conferenciaStatus==null?"SEM_CONFERENCIA":r.conferenciaStatus,
                r==null?null:r.conferidoEm,r==null?null:r.conferenciaJson,c!=null,c!=null&&c.getEmpresaConfirmadaEm()!=null,c==null?null:c.getEmpresaExternaId()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(saida);
    }
}
