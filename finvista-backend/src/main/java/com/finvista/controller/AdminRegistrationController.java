package com.finvista.controller;

import com.finvista.dto.ClienteResponse;
import com.finvista.dto.admin.*;
import com.finvista.service.AdminRegistrationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminRegistrationController {

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<java.util.Map<String, String>> conflito() {
        return ResponseEntity.status(409).body(java.util.Map.of("mensagem", "Cadastro não concluído. Confira se o e-mail já está em uso e atualize a listagem."));
    }
    private final AdminRegistrationService service;

    public AdminRegistrationController(AdminRegistrationService service) {
        this.service = service;
    }

    @PostMapping(value = "/clientes", consumes = "application/json", headers = "X-Finvista-Admin=1")
    public ResponseEntity<ClienteResponse> cliente(@Valid @RequestBody AdminClientRequest r) {
        return ResponseEntity.status(201).body(service.criarCliente(r));
    }

    @PostMapping(value = "/usuarios", consumes = "application/json", headers = "X-Finvista-Admin=1")
    public ResponseEntity<AdminUserResponse> usuario(@Valid @RequestBody AdminUserRequest r) {
        return ResponseEntity.status(201).body(service.criarUsuario(r));
    }

    @GetMapping("/usuarios")
    public List<AdminUserResponse> usuarios() {
        return service.listarUsuarios();
    }
}
