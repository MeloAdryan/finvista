package com.finvista.controller;
import com.finvista.service.*;import com.finvista.service.FinancialViewService.*;import java.time.LocalDate;import java.util.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/gestao-financeira")
public class FinancialManagementController {
 private final FinancialManagementService management;private final ContaAzulReconciliationService reconciliation;private final ClienteContextService ctx;private final ManagerialDreService dre;
 public FinancialManagementController(FinancialManagementService management,ContaAzulReconciliationService reconciliation,ClienteContextService ctx,ManagerialDreService dre){this.management=management;this.reconciliation=reconciliation;this.ctx=ctx;this.dre=dre;}
 private LocalDate date(Map<String,String> q,String name){return q.get(name)==null?null:LocalDate.parse(q.get(name));}
 private Filtro filter(Map<String,String> q){return new Filtro(Criterio.valueOf(q.getOrDefault("criterio","COMPETENCIA")),Periodo.valueOf(q.getOrDefault("periodo","PERSONALIZADO")),Situacao.valueOf(q.getOrDefault("situacao","TODAS")),date(q,"referencia"),date(q,"inicio"),date(q,"fim"),q.get("categoria"),q.get("centro"));}
 private <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
 @GetMapping("/saude") public ResponseEntity<FinancialManagementService.Health> health(){return ok(management.health());}
 @GetMapping("/atrasos") public ResponseEntity<FinancialManagementService.Aging> aging(@RequestParam(required=false)String tipo,@RequestParam(required=false)String faixa,@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="20")int tamanho){return ok(management.aging(tipo,faixa,pagina,tamanho));}
 @GetMapping("/realizado-previsto") public ResponseEntity<FinancialManagementService.Forecast> forecast(@RequestParam Map<String,String> q){return ok(management.forecast(date(q,"inicio"),date(q,"fim"),Criterio.valueOf(q.getOrDefault("fonte","BAIXA"))));}
 @GetMapping("/detalhe") public ResponseEntity<FinancialManagementService.Detail> detail(@RequestParam Long id,@RequestParam(required=false)String movimentoId,@RequestParam(defaultValue="false")boolean posicao,@RequestParam Map<String,String> q){return ok(management.detail(id,movimentoId,filter(q),posicao));}
 @GetMapping("/conferencia") public ResponseEntity<ContaAzulReconciliationService.Resultado> reconcile(@RequestParam Map<String,String> q){return ok(reconciliation.conferirLeitura(ctx.getClienteAtualId(),date(q,"inicio"),date(q,"fim")));}
 @GetMapping("/dre") public ResponseEntity<ManagerialDreService.Result> dre(@RequestParam Map<String,String> q){return ok(dre.report(date(q,"inicio"),date(q,"fim")));}
 @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class}) public ResponseEntity<Map<String,String>> invalid(Exception e){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("mensagem",e.getMessage()==null?"Confira os parâmetros informados.":e.getMessage()));}
}
