package com.finvista.service;

import com.finvista.dto.ClienteResponse;
import com.finvista.dto.admin.AdminClientUpdateRequest;
import com.finvista.model.*;
import com.finvista.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@ Service 

    public class AdminClientManagementService {

        private final ClienteContextService contexto;
        private final ClienteRepository clientes;
        private final AdminClientChangeRepository alteracoes;
        private final EntityManager em;
        private CadastroReferenceGuard references;

        public AdminClientManagementService(ClienteContextService contexto, ClienteRepository clientes,
                AdminClientChangeRepository alteracoes, EntityManager em) {
            this.contexto = contexto;
            this.clientes = clientes;
            this.alteracoes = alteracoes;
            this.em = em;
        }

        @org.springframework.beans.factory.annotation.Autowired
        public AdminClientManagementService(ClienteContextService contexto, ClienteRepository clientes, AdminClientChangeRepository alteracoes, EntityManager em, CadastroReferenceGuard references) {
            this(contexto, clientes, alteracoes, em);
            this.references = references;
        }

        @Transactional(readOnly = true)
        public List<ClienteResponse> listar() {
            contexto.validarAdministrador();
            return clientes.findAll().stream()
                    .sorted(Comparator.comparing(Cliente::getNome, String.CASE_INSENSITIVE_ORDER))
                    .map(this::resposta).toList();
        }

        @Transactional
        public ClienteResponse atualizar(Long id, AdminClientUpdateRequest pedido) {
            contexto.validarAdministrador();
            if (id == null || id <= 0 || pedido == null || !nomeValido(pedido.nome())
                    || !nomeValido(pedido.nomeAnterior()) || pedido.ativo() == null || pedido.ativoAnterior() == null) {
                throw new IllegalArgumentException("Informe o nome, o status e o cadastro exibido no formulário.");
            }
            // Serializa alterações do mesmo cliente; o lock é mantido até o fim da transação.
            Cliente cliente = em.find(Cliente.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (cliente == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada.");
            }
            if (!Objects.equals(cliente.getNome(), pedido.nomeAnterior())
                    || !Objects.equals(cliente.getAtivo(), pedido.ativoAnterior())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O cadastro mudou desde a abertura do formulário. Atualize a lista e abra a edição novamente.");
            }
            String novoNome = pedido.nome().trim();
            if (Objects.equals(cliente.getNome(), novoNome) && Objects.equals(cliente.getAtivo(), pedido.ativo())) {
                return resposta(cliente);
            }
            Usuario autor = contexto.getUsuarioAutenticado();
            var historico = new AdminClientChange(id, autor, cliente.getNome(), novoNome, cliente.getAtivo(), pedido.ativo());
            cliente.setNome(novoNome);
            cliente.setAtivo(pedido.ativo());
            clientes.save(cliente);
            alteracoes.save(historico); // Mesma transação: falha no registro impede confirmar a alteração.
            return resposta(cliente);
        }

        @Transactional
        public void excluir(Long id, com.finvista.dto.admin.AdminDeleteRequest r) {
            contexto.validarAdministrador();
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Empresa inválida.");
            }
            var c = em.find(Cliente.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (c == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada.");
            }
            if (r == null || !"EXCLUIR".equals(r.confirmacao()) || !Objects.equals(c.getNome(), r.nomeAnterior()) || !Objects.equals(c.getAtivo(), r.ativoAnterior())) {
                throw new IllegalArgumentException("Confirme a exclusão com o cadastro atual.");
            }
            if (references.empresa(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A empresa possui usuários, dados, conexão ou histórico. Desative-a para preservar os vínculos.");
            }
            clientes.delete(c);
            clientes.flush();
            if (Objects.equals(id, contexto.getClienteSelecionadoId())) {
                contexto.limparClienteSelecionado();
            }
        }

        private boolean nomeValido(String nome) {
            return nome != null && !nome.isBlank() && nome.length() <= 150;
        }

        private ClienteResponse resposta(Cliente c) {
            return new ClienteResponse(c.getId(), c.getNome(), c.getAtivo(), c.getDataCriacao());
        }
    }
         
