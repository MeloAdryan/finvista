package com.finvista.config;

import com.finvista.model.Cliente;
import com.finvista.model.Usuario;
import com.finvista.repository.ClienteRepository;
import com.finvista.repository.UsuarioRepository;
import com.finvista.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClienteInitializer implements CommandLineRunner {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;

    @Value("${finvista.cliente.nome:}")
    private String nome;

    @Value("${finvista.cliente.email:}")
    private String email;

    @Value("${finvista.cliente.senha:}")
    private String senha;

    public ClienteInitializer(
            UsuarioService usuarioService,
            UsuarioRepository usuarioRepository,
            ClienteRepository clienteRepository
    ) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {

        if (nome == null || nome.isBlank()
                || email == null || email.isBlank()
                || senha == null || senha.isBlank()) {
            return;
        }

        Usuario usuario = usuarioService
                .buscarPorEmail(email)
                .orElseGet(() ->
                        usuarioService.criarUsuario(
                                nome,
                                email,
                                senha,
                                "CLIENTE"
                        )
                );

        /*
         * Compatibilidade com bancos ja existentes.
         *
         * Versoes anteriores criavam o usuario CLIENTE,
         * mas nao criavam a entidade Cliente nem faziam
         * a associacao entre ambos.
         */
        if (usuario.getCliente() != null) {
            return;
        }

        Cliente cliente = new Cliente(nome.trim());

        Cliente clienteSalvo =
                clienteRepository.save(cliente);

        usuario.setCliente(clienteSalvo);

        usuarioRepository.save(usuario);

        System.out.println(
                "Cliente inicial do FinVista configurado com sucesso.");
    }
}