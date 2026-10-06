package com.finvista.service;

import com.finvista.dto.ClienteResponse;
import com.finvista.model.Cliente;
import com.finvista.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClienteContextService clienteContextService;

    public ClienteService(
            ClienteRepository clienteRepository,
            ClienteContextService clienteContextService
    ) {
        this.clienteRepository = clienteRepository;
        this.clienteContextService = clienteContextService;
    }

    public List<ClienteResponse> listarClientesAtivos() {

        validarAdministrador();

        return clienteRepository
                .findAllByAtivoTrueOrderByNomeAsc()
                .stream()
                .map(this::criarResposta)
                .toList();
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

    private void validarAdministrador() {

        if (!clienteContextService.isAdmin()) {
            throw new IllegalStateException(
                    "Apenas administradores podem acessar a carteira de clientes."
            );
        }
    }
}
