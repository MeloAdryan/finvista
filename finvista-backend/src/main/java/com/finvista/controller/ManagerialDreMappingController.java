package com.finvista.controller;
import com.finvista.service.ManagerialDreService;import com.finvista.model.DreCategoryMapping;import java.util.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/gestao-financeira/dre-mapeamentos")
public class ManagerialDreMappingController {
 private final ManagerialDreService service;public ManagerialDreMappingController(ManagerialDreService service){this.service=service;}
 @PostMapping public ResponseEntity<DreCategoryMapping> save(@RequestBody ManagerialDreService.MappingRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(request));}
 @DeleteMapping("/{id}") public ResponseEntity<Void> remove(@PathVariable UUID id,@RequestParam long versao){service.remove(id,versao);return ResponseEntity.noContent().build();}
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("mensagem",e.getMessage()));}
 @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class) public ResponseEntity<Map<String,String>> conflict(Exception e){return ResponseEntity.status(409).body(Map.of("mensagem","O mapeamento foi alterado por outra sessão. Consulte novamente."));}
}
