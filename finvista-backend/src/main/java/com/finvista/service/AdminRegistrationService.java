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

    public AdminRegistrationService(ClienteContextService context, ClienteRepository clients, UsuarioRepository users, UsuarioService userService) {
        this.context = context;
        this.clients = clients;
        this.users = users;
        this.userService = userService;
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

    private AdminUserResponse resposta(Usuario u) {
        Cliente c = u.getCliente();
        return new AdminUserResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), c == null ? null : c.getId(), c == null ? null : c.getNome(), u.getAtivo());
    }
}
