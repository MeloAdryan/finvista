package com.finvista.controller;

import com.finvista.dto.ClienteResponse;
import com.finvista.model.Cliente;
import com.finvista.service.ClienteContextService;
import com.finvista.service.ClienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteContextService clienteContextService;

    public ClienteController(
            ClienteService clienteService,
            ClienteContextService clienteContextService
    ) {
        this.clienteService = clienteService;
        this.clienteContextService = clienteContextService;
    }

    
    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listar() {

        return ResponseEntity.ok(
                clienteService.listarClientesAtivos()
        );
    }


    @PostMapping("/contexto/{clienteId}")
    public ResponseEntity<ClienteResponse> selecionarCliente(
            @PathVariable Long clienteId
    ) {

        Cliente cliente =
                clienteContextService.selecionarCliente(
                        clienteId
                );

        return ResponseEntity.ok(
                criarResposta(cliente)
        );
    }


    @GetMapping("/contexto")
    public ResponseEntity<ClienteResponse> contextoAtual() {

        Cliente cliente =
                clienteContextService.getClienteAtual();

        return ResponseEntity.ok(
                criarResposta(cliente)
        );
    }


    @DeleteMapping("/contexto")
    public ResponseEntity<Void> limparContexto() {

        clienteContextService.validarAdministrador();

        clienteContextService.limparClienteSelecionado();

        return ResponseEntity.noContent().build();
    }

    private ClienteResponse criarResposta(
            Cliente cliente
    ) {

        return new ClienteResponse(
                cliente.getId(),
                cliente.getNome(),
                cliente.getAtivo(),
                cliente.getDataCriacao()
        );
    }
}