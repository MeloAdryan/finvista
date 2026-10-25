package com.finvista.controller;
import com.finvista.service.FinancialLedgerService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/analises/financeira/caixa")
public class FinancialLedgerController {
 private final FinancialLedgerService ledger;
 public FinancialLedgerController(FinancialLedgerService ledger){this.ledger=ledger;}
 @GetMapping public ResponseEntity<FinancialLedgerService.Result> consultar(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim){try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ledger.consultar(inicio,fim));}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
}
