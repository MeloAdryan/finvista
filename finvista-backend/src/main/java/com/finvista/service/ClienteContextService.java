package com.finvista.service;

import com.finvista.model.Cliente;
import com.finvista.model.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class ClienteContextService {

    private final UsuarioService usuarioService;

    public ClienteContextService(
            UsuarioService usuarioService
    ) {
        this.usuarioService = usuarioService;
    }

    /**
     * Retorna o usuário atualmente autenticado na sessão.
     */
    public Usuario getUsuarioAutenticado() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {

            throw new IllegalStateException(
                    "Nenhum usuário autenticado foi encontrado."
            );
        }

        Usuario usuario = usuarioService
                .buscarPorEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "O usuário autenticado não foi encontrado."
                        )
                );

        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new IllegalStateException(
                    "O usuário autenticado está inativo."
            );
        }

        return usuario;
    }

    /**
     * Retorna o Cliente associado ao usuário autenticado.
     *
     * Usuários comuns precisam obrigatoriamente possuir
     * um Cliente associado.
     */
    public Cliente getClienteDoUsuarioAutenticado() {

        Usuario usuario = getUsuarioAutenticado();

        Cliente cliente = usuario.getCliente();

        if (cliente == null) {
            throw new IllegalStateException(
                    "O usuário autenticado não possui um cliente associado."
            );
        }

        if (!Boolean.TRUE.equals(cliente.getAtivo())) {
            throw new IllegalStateException(
                    "O cliente associado ao usuário está inativo."
            );
        }

        return cliente;
    }

    /**
     * Informa se o usuário autenticado possui perfil ADMIN.
     */
    public boolean isAdmin() {

        Usuario usuario = getUsuarioAutenticado();

        return "ADMIN".equalsIgnoreCase(
                usuario.getPerfil()
        );
    }
}