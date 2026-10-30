package com.finvista.service;

import com.finvista.model.Usuario;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Server-side session stamp; never includes a password in a response.
 */
public final class AuthSessionProof {

    private AuthSessionProof() {
    }

    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static String part(Object value) {
        String s = String.valueOf(value);
        return s.length() + ":" + s;
    }

    public static String session(Usuario u) {
        return digest(part(u.getId()) + part(u.getEmail()) + part(u.getAcessoRevisao()) + part(u.getSenhaHash()) + part(u.getPerfil()) + part(u.getAtivo()) + part(u.getCliente() == null ? null : u.getCliente().getId()));
    }

    public static String revision(Usuario u) {
        return digest(session(u) + part(u.getNome()));
    }

    public static boolean same(String a, Object b) {
        return a != null && b instanceof String s && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), s.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean valid(Usuario u, Object proof) {
        return u != null && Boolean.TRUE.equals(u.getAtivo()) && same(session(u), proof);
    }
}
