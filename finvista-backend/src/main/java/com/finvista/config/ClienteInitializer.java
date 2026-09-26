package com.finvista.config;

import com.finvista.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ClienteInitializer implements CommandLineRunner {

    private final UsuarioService usuarioService;

    @Value("${finvista.cliente.nome:}")
    private String nome;

    @Value("${finvista.cliente.email:}")
    private String email;

    @Value("${finvista.cliente.senha:}")
    private String senha;

    public ClienteInitializer(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(String... args) {

        if (nome == null || nome.isBlank()
                || email == null || email.isBlank()
                || senha == null || senha.isBlank()) {
            return;
        }

        if (usuarioService.buscarPorEmail(email).isPresent()) {
            return;
        }

        usuarioService.criarUsuario(
                nome,
                email,
                senha,
                "CLIENTE");

        System.out.println(
                "Cliente inicial do FinVista criado com sucesso.");
    }
}