package com.finvista.service;

import com.finvista.model.Cliente;
import com.finvista.model.Usuario;
import com.finvista.repository.ClienteRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class ClienteContextService {

    public static final String CLIENTE_SELECIONADO_SESSION_KEY =
            "FINVISTA_CLIENTE_SELECIONADO_ID";

    private final UsuarioService usuarioService;
    private final ClienteRepository clienteRepository;
    private final HttpServletRequest httpServletRequest;

    public ClienteContextService(
            UsuarioService usuarioService,
            ClienteRepository clienteRepository,
            HttpServletRequest httpServletRequest
    ) {
        this.usuarioService = usuarioService;
        this.clienteRepository = clienteRepository;
        this.httpServletRequest = httpServletRequest;
    }

    /**
     * Retorna o usuário atualmente autenticado.
     */
    public Usuario getUsuarioAutenticado() {

        Authentication authentication = SecurityContextHolder
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
                .orElseThrow(() -> new IllegalStateException(
                        "O usuário autenticado não foi encontrado."
                ));

        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new IllegalStateException(
                    "O usuário autenticado está inativo."
            );
        }

        return usuario;
    }

    /**
     * Retorna o cliente associado diretamente ao usuário.
     *
     * Este método continua sendo útil para usuários comuns.
     */
    public Cliente getClienteDoUsuarioAutenticado() {

        Usuario usuario = getUsuarioAutenticado();

        Cliente cliente = usuario.getCliente();

        if (cliente == null) {
            throw new IllegalStateException(
                    "O usuário autenticado não possui um cliente associado."
            );
        }

        validarClienteAtivo(cliente);

        return cliente;
    }

    /**
     * Retorna o cliente que deve ser utilizado nas operações
     * financeiras do FinVista.
     *
     * USUARIO comum:
     *     utiliza automaticamente o Cliente associado ao usuário.
     *
     * ADMIN:
     *     utiliza o Cliente atualmente selecionado na sessão.
     */
    public Cliente getClienteAtual() {

        if (!isAdmin()) {
            return getClienteDoUsuarioAutenticado();
        }

        Long clienteId = getClienteSelecionadoId();

        if (clienteId == null) {
            throw new IllegalStateException(
                    "Nenhum cliente foi selecionado pelo administrador."
            );
        }

        return clienteRepository
                .findByIdAndAtivoTrue(clienteId)
                .orElseThrow(() -> {
                    limparClienteSelecionado();

                    return new IllegalStateException(
                            "O cliente selecionado não existe ou está inativo."
                    );
                });
    }

    /**
     * Retorna somente o ID do cliente atual.
     */
    public Long getClienteAtualId() {
        return getClienteAtual().getId();
    }

    /**
     * Seleciona um cliente para o ADMIN.
     *
     * O cliente selecionado fica armazenado na HttpSession.
     */
    public Cliente selecionarCliente(
            Long clienteId
    ) {

        validarAdministrador();

        if (clienteId == null) {
            throw new IllegalArgumentException(
                    "O ID do cliente é obrigatório."
            );
        }

        Cliente cliente = clienteRepository
                .findByIdAndAtivoTrue(clienteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cliente não encontrado ou inativo."
                ));

        HttpSession session =
                httpServletRequest.getSession(true);

        session.setAttribute(
                CLIENTE_SELECIONADO_SESSION_KEY,
                cliente.getId()
        );

        return cliente;
    }

    /**
     * Retorna o ID armazenado na sessão do ADMIN.
     *
     * Caso ainda não exista uma sessão ou nenhum cliente tenha
     * sido selecionado, retorna null.
     */
    public Long getClienteSelecionadoId() {

        HttpSession session =
                httpServletRequest.getSession(false);

        if (session == null) {
            return null;
        }

        Object valor = session.getAttribute(
                CLIENTE_SELECIONADO_SESSION_KEY
        );

        if (valor == null) {
            return null;
        }

        if (valor instanceof Long clienteId) {
            return clienteId;
        }

        if (valor instanceof Number numero) {
            return numero.longValue();
        }

        try {
            return Long.valueOf(valor.toString());
        } catch (NumberFormatException exception) {

            session.removeAttribute(
                    CLIENTE_SELECIONADO_SESSION_KEY
            );

            return null;
        }
    }

    /**
     * Remove o cliente selecionado da sessão.
     */
    public void limparClienteSelecionado() {

        HttpSession session =
                httpServletRequest.getSession(false);

        if (session != null) {
            session.removeAttribute(
                    CLIENTE_SELECIONADO_SESSION_KEY
            );
        }
    }

    /**
     * Informa se existe um cliente selecionado pelo ADMIN.
     */
    public boolean possuiClienteSelecionado() {
        return getClienteSelecionadoId() != null;
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

    /**
     * Garante que somente ADMIN execute determinadas operações.
     */
    public void validarAdministrador() {

        if (!isAdmin()) {
            throw new IllegalStateException(
                    "Apenas administradores podem executar esta operação."
            );
        }
    }

    private void validarClienteAtivo(
            Cliente cliente
    ) {

        if (!Boolean.TRUE.equals(cliente.getAtivo())) {
            throw new IllegalStateException(
                    "O cliente associado ao usuário está inativo."
            );
        }
    }
}