package com.finvista.service;

import com.finvista.model.*;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Counts references only; no cascades, deletes, dynamic user input or guessed
 * identities.
 */
@Service
public class CadastroReferenceGuard {

    private final EntityManager em;

    public CadastroReferenceGuard(EntityManager em) {
        this.em = em;
    }

    public boolean empresa(Long id) {
        return referenced(Cliente.class, Set.of("clienteId"), id);
    }

    public boolean usuario(Long id) {
        return referenced(Usuario.class, Set.of("usuarioId", "autorizadoPor"), id);
    }

    private boolean referenced(Class<?> type, Set<String> scalarNames, Long id) {
        for (var entity : em.getMetamodel().getEntities()) {
            for (var attr : entity.getAttributes()) {
                boolean relation = attr.getJavaType() == type;
                boolean scalar = attr.getJavaType() == Long.class && scalarNames.contains(attr.getName());
                if (!relation && !scalar) {
                    continue;
                }
                String name = entity.getName(), field = attr.getName();
                if (!name.matches("[A-Za-z_][A-Za-z_0-9]*") || !field.matches("[A-Za-z_][A-Za-z_0-9]*")) {
                    throw new IllegalStateException("Referência não reconhecida; exclusão bloqueada.");
                }
                String hql = "select count(e) from " + name + " e where e." + field + (relation ? ".id" : "") + " = :id";
                if (em.createQuery(hql, Long.class).setParameter("id", id).getSingleResult() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
