package com.finvista.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.dto.AlertCenterResponse;
import com.finvista.dto.AlertCenterResponse.*;
import com.finvista.dto.FinancialAlertsResponse.*;
import com.finvista.model.*;
import com.finvista.model.FinancialAlertRecord.Estado;
import com.finvista.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertCenterService {

    private final FinancialAlertsService regras;
    private final FinancialAlertRecordRepository registros;
    private final FinancialAlertReadRepository leituras;
    private final ClienteRepository clientes;
    private final ClienteContextService contexto;
    private final FinancialReferenceService referencia;
    private final ObjectMapper json;
    private final Clock clock;

    public AlertCenterService(FinancialAlertsService regras, FinancialAlertRecordRepository registros,
            FinancialAlertReadRepository leituras, ClienteRepository clientes, ClienteContextService contexto,
            FinancialReferenceService referencia, ObjectMapper json, Clock clock) {
        this.regras = regras;
        this.registros = registros;
        this.leituras = leituras;
        this.clientes = clientes;
        this.contexto = contexto;
        this.referencia = referencia;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public Atualizacao atualizar(LocalDate inicio, LocalDate fim) {
        Long clienteId = contexto.getClienteAtualId();
        Cliente cliente = bloquear(clienteId);
        var resposta = regras.consultarCompleto(inicio, fim);
        conferirCliente(clienteId);
        if (!clienteId.equals(resposta.clienteId())) {
            throw new IllegalStateException("A empresa da análise mudou.");
        }
        Instant agora = clock.instant();
        var existentes = registros.findByClienteIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqual(
                clienteId, resposta.fim(), resposta.inicio()).stream()
                .collect(Collectors.toMap(FinancialAlertRecord::getChave, Function.identity()));
        Set<String> presentes = new HashSet<>();
        for (var alerta : resposta.alertas()) {
            presentes.add(alerta.id());
            var registro = existentes.get(alerta.id());
            if (registro == null) {
                registro = new FinancialAlertRecord(cliente, alerta, escrever(alerta), agora);
            } else {
                registro.atualizar(alerta, escrever(alerta), agora);
            }
            registros.save(registro);
        }
        for (var registro : existentes.values()) {
            boolean mesmoIntervalo = registro.getTipo() != Tipo.AUMENTO_DESPESAS
                    || (registro.getDataInicio().equals(resposta.inicio()) && registro.getDataFim().equals(resposta.fim()));
            boolean avaliavel = registro.getTipo() != Tipo.AUMENTO_DESPESAS || resposta.comparacao() == Comparacao.DISPONIVEL;
            boolean cruzaPeriodo = !registro.getDataInicio().isAfter(resposta.fim()) && !registro.getDataFim().isBefore(resposta.inicio());
            if (!presentes.contains(registro.getChave()) && mesmoIntervalo && avaliavel && cruzaPeriodo) {
                registro.resolver(agora);
                registros.save(registro);
            }
        }
        conferirCliente(clienteId);
        registros.flush();
        return new Atualizacao(clienteId, resposta.inicio(), resposta.fim(), agora, resposta.total(), resposta.comparacao());
    }

    @Transactional(readOnly = true)
    public AlertCenterResponse listar(LocalDate inicio, LocalDate fim, Gravidade gravidade, Tipo tipo,
            Estado estado, Boolean lido, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 50) {
            throw new IllegalArgumentException("Paginação inválida.");
        }
        Periodo periodo = periodo(inicio, fim);
        Long clienteId = contexto.getClienteAtualId();
        Long usuarioId = contexto.getUsuarioAutenticado().getId();
        Specification<FinancialAlertRecord> filtro = (root, query, cb) -> {
            List<Predicate> criterios = new ArrayList<>();
            criterios.add(cb.equal(root.get("cliente").get("id"), clienteId));
            criterios.add(cb.lessThanOrEqualTo(root.get("dataInicio"), periodo.fim()));
            criterios.add(cb.greaterThanOrEqualTo(root.get("dataFim"), periodo.inicio()));
            if (gravidade != null) {
                criterios.add(cb.equal(root.get("gravidade"), gravidade));
            }
            if (tipo != null) {
                criterios.add(cb.equal(root.get("tipo"), tipo));
            }
            if (estado != null) {
                criterios.add(cb.equal(root.get("estado"), estado));
            }
            if (lido != null) {
                var leitura = query.subquery(Integer.class);
                var receipt = leitura.from(FinancialAlertRead.class);
                leitura.select(cb.literal(1)).where(cb.equal(receipt.get("alerta").get("id"), root.get("id")),
                        cb.equal(receipt.get("usuario").get("id"), usuarioId),
                        cb.greaterThanOrEqualTo(receipt.get("lidoEm"), root.get("notificadoEm")));
                criterios.add(lido ? cb.exists(leitura) : cb.not(cb.exists(leitura)));
            }
            return cb.and(criterios.toArray(Predicate[]::new));
        };
        var resultado = registros.findAll(filtro, PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Order.asc("estado"), Sort.Order.desc("prioridade"), Sort.Order.desc("alteradoEm"), Sort.Order.desc("id"))));
        var ids = resultado.getContent().stream().map(FinancialAlertRecord::getId).toList();
        Map<Long, Instant> lidos = ids.isEmpty() ? Map.of() : leituras
                .findByAlertaClienteIdAndUsuarioIdAndAlertaIdIn(clienteId, usuarioId, ids).stream()
                .collect(Collectors.toMap(item -> item.getAlerta().getId(), FinancialAlertRead::getLidoEm));
        var itens = resultado.getContent().stream().map(registro -> {
            Instant leitura = lidos.get(registro.getId());
            return new Registro(registro.getId(), ler(registro.getDados()), registro.getEstado(),
                    leitura != null && !leitura.isBefore(registro.getNotificadoEm()), registro.getOcorrencias(),
                    registro.getCriadoEm(), registro.getAlteradoEm(), registro.getVerificadoEm(), registro.getResolvidoEm());
        }).toList();
        conferirCliente(clienteId);
        return new AlertCenterResponse(clienteId, periodo.inicio(), periodo.fim(), pagina, tamanho,
                resultado.getTotalElements(), resultado.getTotalPages(), itens);
    }

    @Transactional
    public void marcarLido(Long id) {
        Long clienteId = contexto.getClienteAtualId();
        bloquear(clienteId);
        var registro = registros.findByIdAndClienteId(id, clienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta não encontrado nesta empresa."));
        Usuario usuario = contexto.getUsuarioAutenticado();
        var leitura = leituras.findByAlertaIdAndUsuarioId(id, usuario.getId());
        if (leitura.isEmpty()) {
            leituras.save(new FinancialAlertRead(registro, usuario, clock.instant()));
        } else if (leitura.get().getLidoEm().isBefore(registro.getNotificadoEm())) {
            leitura.get().marcar(clock.instant());
            leituras.save(leitura.get());
        }
        conferirCliente(clienteId);
    }

    private Cliente bloquear(Long clienteId) {
        return clientes.bloquearAtivoPorId(clienteId)
                .orElseThrow(() -> new IllegalStateException("Empresa não encontrada ou inativa."));
    }

    private void conferirCliente(Long clienteId) {
        if (!clienteId.equals(contexto.getClienteAtualId())) {
            throw new IllegalStateException("A empresa selecionada mudou. Atualize a central.");
        }
    }

    private Periodo periodo(LocalDate inicio, LocalDate fim) {
        if ((inicio == null) != (fim == null)) {
            throw new IllegalArgumentException("Informe as duas datas.");
        }
        if (inicio == null) {
            YearMonth mes = referencia.obterMesReferencia();
            inicio = mes.atDay(1);
            fim = mes.atEndOfMonth();
        }
        if (fim.isBefore(inicio) || ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(fim)) > 23) {
            throw new IllegalArgumentException("Escolha um período válido de até 24 meses.");
        }
        return new Periodo(inicio, fim);
    }

    private String escrever(Alerta alerta) {
        try {
            return json.writeValueAsString(alerta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível registrar o alerta.");
        }
    }

    private Alerta ler(String dados) {
        try {
            return json.readValue(dados, Alerta.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível ler o histórico do alerta.");
        }
    }
}
