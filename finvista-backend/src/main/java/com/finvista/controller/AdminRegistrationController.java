package com.finvista.controller;

import com.finvista.dto.ClienteResponse;
import com.finvista.dto.admin.*;
import com.finvista.service.AdminRegistrationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ RestController 

    @RequestMapping("/api/admin")
    public class AdminRegistrationController {

        @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
        public ResponseEntity<java.util.Map<String, String>> conflito() {
            return ResponseEntity.status(409).body(java.util.Map.of("mensagem", "Cadastro não concluído. Confira se o e-mail já está em uso e atualize a listagem."));
        }

        @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
        public ResponseEntity<java.util.Map<String, String>> status(org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).cacheControl(org.springframework.http.CacheControl.noStore()).body(java.util.Map.of("mensagem", e.getReason() == null ? "Operação não concluída." : e.getReason()));
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

        @PutMapping(value = "/usuarios/{id}", consumes = "application/json", headers = "X-Finvista-Admin=1")
        public AdminUserResponse atualizar(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest r) {
            return service.atualizarUsuario(id, r);
        }

        @DeleteMapping(value = "/usuarios/{id}", consumes = "application/json", headers = "X-Finvista-Admin=1")
        public java.util.Map<String, Boolean> excluir(@PathVariable Long id, @Valid @RequestBody AdminDeleteRequest r) {
            service.excluirUsuario(id, r);
            return java.util.Map.of("excluido", true);
        }

        @GetMapping("/usuarios")
        public List<AdminUserResponse> usuarios() {
            return service.listarUsuarios();
        }
    }
