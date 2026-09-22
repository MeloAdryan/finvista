package com.finvista.service;

import com.finvista.dto.auth.LoginRequest;
import com.finvista.dto.auth.LoginResponse;
import com.finvista.model.Usuario;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioService usuarioService;

    public AuthService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public LoginResponse login(LoginRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados de login são obrigatórios."
            );
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException(
                    "E-mail é obrigatório."
            );
        }

        if (request.senha() == null || request.senha().isBlank()) {
            throw new IllegalArgumentException(
                    "Senha é obrigatória."
            );
        }

        Usuario usuario = usuarioService
                .buscarPorEmail(request.email())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "E-mail ou senha inválidos."
                        )
                );

        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new IllegalArgumentException(
                    "Usuário inativo."
            );
        }

        if (!usuarioService.verificarSenha(
                usuario,
                request.senha()
        )) {
            throw new IllegalArgumentException(
                    "E-mail ou senha inválidos."
            );
        }

        return new LoginResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil()
        );
    }
}