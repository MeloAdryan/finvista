package com.finvista.service;

import com.finvista.dto.ClienteResponse;
import com.finvista.model.Cliente;
import com.finvista.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@ Service 

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

        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public List<ClienteResponse> listarClientesAtivos() {
            var usuario = clienteContextService.getUsuarioAutenticado();
            if (!"ADMIN".equalsIgnoreCase(usuario.getPerfil())) {
                return listarEmpresasVinculadas();
            }

            validarAdministrador();

            return clienteRepository
                    .findAllByAtivoTrueOrderByNomeAsc()
                    .stream()
                    .map(this::criarResposta)
                    .toList();
        }

        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public List<ClienteResponse> listarEmpresasVinculadas() {
            var usuario = clienteContextService.getUsuarioAutenticado();
            return vinculosAtivos(usuario);
        }

        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public List<ClienteResponse> listarEmpresasDoCliente(Long usuarioId) {
            var administrador = clienteContextService.getUsuarioAutenticado();
            if (!"ADMIN".equalsIgnoreCase(administrador.getPerfil())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN);
            }
            var usuario = java.util.Optional.ofNullable(entityManager.find(com.finvista.model.Usuario.class, usuarioId))
                    .filter(u -> Boolean.TRUE.equals(u.getAtivo()) && "CLIENTE".equals(u.getPerfil()))
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Cliente ativo não encontrado."));
            return vinculosAtivos(usuario);
        }

        @jakarta.persistence.PersistenceContext
        private jakarta.persistence.EntityManager entityManager;

        private List<ClienteResponse> vinculosAtivos(com.finvista.model.Usuario usuario) {
            return java.util.stream.Stream.concat(java.util.stream.Stream.ofNullable(usuario.getCliente()),
                        usuario.getEmpresasAdicionais().stream())
                        .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                        .sorted(java.util.Comparator.comparing(Cliente::getNome, String.CASE_INSENSITIVE_ORDER))
                        .map(this::criarResposta).toList();
        }

        private ClienteResponse criarResposta(
                Cliente cliente
        ) {
            return new ClienteResponse(
                    cliente.getId(),
                    cliente.getNome(),
                    cliente.getAtivo(),
                    cliente.getDataCriacao(),
                    cliente.getDataModificacao()
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

    