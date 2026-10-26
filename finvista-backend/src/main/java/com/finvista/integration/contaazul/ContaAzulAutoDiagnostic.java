package com.finvista.integration.contaazul;

import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;


final class ContaAzulAutoDiagnostic {
    private ContaAzulAutoDiagnostic() {}
    static String resumir(Throwable erro) {
        var vistos = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var partes = new ArrayList<String>();
        for (Throwable t = erro; t != null && partes.size() < 8 && vistos.add(t); t = t.getCause()) {
            String parte = t.getClass().getName();
            if (t instanceof ResponseStatusException http) parte += " http=" + http.getStatusCode().value();
            else if (t instanceof RestClientResponseException http) parte += " http=" + http.getStatusCode().value();
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().matches("[A-Z0-9]{5}")) parte += " sqlState=" + sql.getSQLState();
            int linhas = 0;
            for (var frame : t.getStackTrace()) {
                if (frame.getClassName().startsWith("com.finvista.") && linhas++ < 4) {
                    parte += " em=" + frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                }
            }
            partes.add(parte);
        }
        return String.join(" <- ", partes);
    }
}
