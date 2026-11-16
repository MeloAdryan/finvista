package com.finvista.service;

import com.finvista.dto.ClienteResponse;
import com.finvista.dto.ProjectionPlanningResponse;
import com.finvista.service.FinancialViewService.Faixa;
import com.finvista.service.FinancialViewService.Filtro;
import com.finvista.service.FinancialViewService.Limite;
import com.finvista.service.FinancialViewService.Periodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GroupPlanningService {

    public record OrcamentoEmpresa(Long empresaId, String empresa, String categoria, String centro, Limite limite) {}

    public record ConferenciaEmpresa(Long empresaId, String empresa, String situacao,
            int movimentosApi, int movimentosExtrato, int divergencias, int ambiguidades,
            int somenteApi, int somenteExtrato, Integer pendenciasEmpresa) {}

    private final ClienteService clientes;
    private final FinancialViewService financeiro;
    private final BudgetPipelineService orcamentos;
    private final ProjectionPlanningService projecao;
    private final ContaAzulReconciliationService conferencia;

    public GroupPlanningService(ClienteService clientes, FinancialViewService financeiro,
            BudgetPipelineService orcamentos, ProjectionPlanningService projecao,
            ContaAzulReconciliationService conferencia) {
        this.clientes = clientes;
        this.financeiro = financeiro;
        this.orcamentos = orcamentos;
        this.projecao = projecao;
        this.conferencia = conferencia;
    }

    private List<ClienteResponse> empresas(Long usuarioAtendido) {
        var vinculadas = usuarioAtendido == null ? clientes.listarEmpresasVinculadas()
                : clientes.listarEmpresasDoCliente(usuarioAtendido);
        if (vinculadas.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma empresa ativa vinculada.");
        }
        return vinculadas.stream().distinct().toList();
    }

    public List<OrcamentoEmpresa> orcamentos(Long usuarioAtendido, Filtro filtro) {
        return empresas(usuarioAtendido).stream().flatMap(empresa -> {
            var registros = financeiro.consultarEmpresa(filtro, empresa.id(), false);
            boolean todos = filtro.periodo() == Periodo.TODOS;
            return orcamentos.configuracoesDaEmpresa(empresa.id()).stream()
                    .filter(o -> todos || FinancialViewService.cruza(o.getDataInicio(), o.getDataFim(), registros.intervalo()))
                    .map(o -> {
                        var faixa = todos ? new Faixa(o.getDataInicio(), o.getDataFim()) : registros.intervalo();
                        var limite = FinancialViewService.limite(o.getId(), o.getNome(), o.getDataInicio(),
                                o.getDataFim(), o.getValorPlanejado(), registros.linhas(), o.getCategoria(),
                                o.getCentroCusto(), faixa);
                        return new OrcamentoEmpresa(empresa.id(), empresa.nome(), o.getCategoria(), o.getCentroCusto(), limite);
                    });
        }).toList();
    }

    public ProjectionPlanningResponse projetar(Long usuarioAtendido, BigDecimal receita, BigDecimal saldo, BigDecimal variacao) {
        var ids = empresas(usuarioAtendido).stream().map(ClienteResponse::id).toList();
        return projecao.simular(receita, saldo, variacao, ids);
    }

    public List<ConferenciaEmpresa> conferir(Long usuarioAtendido, LocalDate inicio, LocalDate fim) {
        return empresas(usuarioAtendido).stream().map(empresa -> {
            var resultado = conferencia.conferirEmpresa(empresa.id(), inicio, fim, true);
            var resumo = resultado.resumo();
            return new ConferenciaEmpresa(empresa.id(), empresa.nome(), resultado.situacao(),
                    resumo.movimentosApi(), resumo.movimentosExtrato(), resumo.divergencias(), resumo.ambiguidades(),
                    resultado.detalhes().somenteApi().size(), resultado.detalhes().somenteExtrato().size(),
                    resultado.cobertura().pendenciasEmpresa());
        }).toList();
    }
}           