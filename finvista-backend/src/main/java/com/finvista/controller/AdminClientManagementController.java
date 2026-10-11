package com.finvista.controller;

import com.finvista.dto.ClienteResponse;
import com.finvista.dto.admin.AdminClientUpdateRequest;
import com.finvista.service.AdminClientManagementService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/clientes")
public class AdminClientManagementController {

    private final AdminClientManagementService service;

    public AdminClientManagementController(AdminClientManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar());
    }

    @PutMapping(value = "/{id}", consumes = "application/json", headers = "X-Finvista-Admin=1")
    public ResponseEntity<ClienteResponse> atualizar(@PathVariable(name = "id") Long id,
            @Valid @RequestBody AdminClientUpdateRequest pedido) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.atualizar(id, pedido));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> falha(ResponseStatusException erro) {
        return ResponseEntity.status(erro.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("mensagem", erro.getReason() == null ? "Falha na alteração do cadastro." : erro.getReason()));
    }
}
