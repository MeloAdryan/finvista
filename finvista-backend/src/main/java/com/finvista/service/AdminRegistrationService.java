package com.finvista.service;

import com.finvista.dto.ClienteResponse;
import com.finvista.dto.admin.*;
import com.finvista.model.*;
import com.finvista.repository.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRegistrationService {

    private final ClienteContextService context;
    private final ClienteRepository clients;
    private final UsuarioRepository users;
    private final UsuarioService userService;
    private jakarta.persistence.EntityManager em;
    private CadastroReferenceGuard references;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminRegistrationService.class);

    public AdminRegistrationService(ClienteContextService context, ClienteRepository clients, UsuarioRepository users, UsuarioService userService) {
        this.context = context;
        this.clients = clients;
        this.users = users;
        this.userService = userService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminRegistrationService(ClienteContextService context, ClienteRepository clients, UsuarioRepository users, UsuarioService userService, jakarta.persistence.EntityManager em, CadastroReferenceGuard references) {
        this(context, clients, users, userService);
        this.em = em;
        this.references = references;
    }

    @Transactional
    public ClienteResponse criarCliente(AdminClientRequest r) {
        context.validarAdministrador();
        if (r.nome() == null || r.nome().isBlank() || r.nome().trim().length() > 150) {
            throw new IllegalArgumentException("Informe um nome de empresa de até 150 caracteres.");
        }
        Cliente c = clients.save(new Cliente(r.nome().trim()));
        return new ClienteResponse(c.getId(), c.getNome(), c.getAtivo(), c.getDataCriacao());
    }

    @Transactional
    public AdminUserResponse criarUsuario(AdminUserRequest r) {
        context.validarAdministrador();
        if (!List.of("ADMIN", "CLIENTE").contains(r.perfil())) {
            throw new IllegalArgumentException("Perfil inválido.");
        }
        if (r.senha() == null || r.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter até 72 bytes em UTF-8.");
        }
        Cliente cliente = null;
        if ("CLIENTE".equals(r.perfil())) {
            if (r.clienteId() == null) {
                throw new IllegalArgumentException("Escolha a empresa do usuário CLIENTE.");
            }
            cliente = clients.findByIdAndAtivoTrue(r.clienteId()).orElseThrow(() -> new IllegalArgumentException("Empresa não encontrada ou inativa."));
        } else if (r.clienteId() != null) {
            throw new IllegalArgumentException("ADMIN administra a carteira e não deve ter uma empresa vinculada neste cadastro.");
        }
        Usuario u = userService.criarUsuario(r.nome(), r.email().trim().toLowerCase(Locale.ROOT), r.senha(), r.perfil());
        u.setCliente(cliente);
        return resposta(users.save(u));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listarUsuarios() {
        context.validarAdministrador();
        return users.findAll().stream().sorted(java.util.Comparator.comparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER)).map(this::resposta).toList();
    }

    private java.util.List<Usuario> lockAdmins() {
        return em.createQuery("select u from Usuario u where upper(u.perfil) = 'ADMIN' and u.ativo = true order by u.id", Usuario.class)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    private Usuario locked(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Usuário inválido.");
        }
        var u = em.find(Usuario.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (u == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        }
        return u;
    }

    private void protect(Usuario u, java.util.List<Usuario> admins) {
        if (java.util.Objects.equals(u.getId(), context.getUsuarioAutenticado().getId())) {
            throw new IllegalArgumentException("Você não pode excluir ou desativar sua própria conta nesta tela.");
        }
        if ("ADMIN".equals(u.getPerfil()) && Boolean.TRUE.equals(u.getAtivo()) && admins.size() <= 1) {
            throw new IllegalArgumentException("O último administrador ativo deve ser preservado.");
        }
    }

    @Transactional
    public AdminUserResponse atualizarUsuario(Long id, AdminUserUpdateRequest r) {
        context.validarAdministrador();
        Long author = context.getUsuarioAutenticado().getId();
        var admins = lockAdmins();
        var u = locked(id);
        if (r == null || r.nome() == null || r.nome().isBlank() || r.nome().trim().length() > 120 || r.email() == null || r.email().isBlank() || r.email().length() > 180 || r.ativo() == null) {
            throw new IllegalArgumentException("Confira nome, e-mail e status.");
        }
        if (!AuthSessionProof.same(AuthSessionProof.revision(u), r.revisao())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "O usuário mudou. Atualize a lista e abra a edição novamente.");
        }
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new IllegalArgumentException("Já existe um usuário cadastrado com este e-mail.");
        }
        if (!r.ativo()) {
            protect(u, admins);
        }
        boolean reset = r.novaSenha() != null && !r.novaSenha().isEmpty();
        if (reset) {
            if (!r.novaSenha().equals(r.confirmarSenha())) {
                throw new IllegalArgumentException("A confirmação da nova senha não confere.");
            }
            userService.definirSenha(u, r.novaSenha());
        } else if (r.confirmarSenha() != null && !r.confirmarSenha().isEmpty()) {
            throw new IllegalArgumentException("Informe a nova senha e sua confirmação.");
        }
        u.setNome(r.nome().trim());
        u.setEmail(email);
        u.setAtivo(r.ativo());
        // Perfil and company are deliberately preserved, including during password resets.
        var result = resposta(users.saveAndFlush(u));
        log.info("Cadastro de usuário atualizado: usuario={} autor={} senhaRedefinida={}", id, author, reset);
        return result;
    }

    @Transactional
    public void excluirUsuario(Long id, AdminDeleteRequest r) {
        context.validarAdministrador();
        var admins = lockAdmins();
        var u = locked(id);
        protect(u, admins);
        if (r == null || !"EXCLUIR".equals(r.confirmacao()) || !AuthSessionProof.same(AuthSessionProof.revision(u), r.revisao())) {
            throw new IllegalArgumentException("Confirme a exclusão com o cadastro atual. Atualize a lista se necessário.");
        }
        if (references.usuario(id)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "O usuário possui histórico ou vínculos. Desative o acesso para preservar os registros.");
        }
        users.delete(u);
        users.flush();
        log.info("Usuário sem vínculos excluído: usuario={} autor={}", id, context.getUsuarioAutenticado().getId());
    }

    private AdminUserResponse resposta(Usuario u) {
        Cliente c = u.getCliente();
        return new AdminUserResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), c == null ? null : c.getId(), c == null ? null : c.getNome(), u.getAtivo(), AuthSessionProof.revision(u));
    }
}
