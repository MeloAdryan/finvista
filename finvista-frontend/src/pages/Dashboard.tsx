import { useEffect, useState } from 'react'

import {
  getDashboard,
  type DashboardData,
} from '../services/dashboardService'

import {
  getProjection,
  type ProjectionData,
} from '../services/projectionService'

import {
  getCashFlow,
  type CashFlowData,
} from '../services/cashFlowService'

import {
  getCostCenters,
  type CostCenterData,
} from '../services/costCenterService'

import {
  getExpenseDistribution,
  type ExpenseDistributionData,
} from '../services/expenseDistributionService'

import {
  getBudgetPipeline,
  type BudgetPipelineData,
} from '../services/budgetPipelineService'

import {
  getHistory,
  type HistoryData,
} from '../services/historyService'

import RevenueExpenseChart from '../charts/RevenueExpenseChart'
import ResultMarginChart from '../charts/ResultMarginChart'
import ProjectionChart from '../charts/ProjectionChart'
import CashFlowChart from '../charts/CashFlowChart'
import CostCenterChart from '../charts/CostCenterChart'
import ExpenseDistributionChart from '../charts/ExpenseDistributionChart'
import BudgetPipelineChart from '../charts/BudgetPipelineChart'
import HistoryChart from '../charts/HistoryChart'

import '../styles/dashboard.css'

function Dashboard() {
  const [dados, setDados] =
    useState<DashboardData | null>(null)
  const [periodoInicial, setPeriodoInicial] =
  useState('')

const [periodoFinal, setPeriodoFinal] = 
useState('')

const [carregandoHistorico,setCarregandoHistorico,] = useState(false)

const [erroHistorico, setErroHistorico,] = useState<string | null>(null)
  const [projecao, setProjecao] =
    useState<ProjectionData[]>([])

  const [fluxoCaixa, setFluxoCaixa] =
    useState<CashFlowData[]>([])

  const [centrosCusto, setCentrosCusto] =
    useState<CostCenterData[]>([])

  const [
    distribuicaoDespesas,
    setDistribuicaoDespesas,
  ] = useState<ExpenseDistributionData[]>([])

  const [orcamentos, setOrcamentos] =
    useState<BudgetPipelineData[]>([])

  const [historico, setHistorico] =
    useState<HistoryData[]>([])

  const [carregando, setCarregando] =
    useState(true)

  const [erro, setErro] =
    useState<string | null>(null)

  useEffect(() => {
    async function carregarDashboard() {
      try {
        const resultado =
          await getDashboard()

        setDados(resultado)

        const dadosProjecao =
          await getProjection()

        setProjecao(dadosProjecao)

        const dadosFluxoCaixa =
          await getCashFlow()

        setFluxoCaixa(dadosFluxoCaixa)

        const dadosCentrosCusto =
          await getCostCenters()

        setCentrosCusto(
          dadosCentrosCusto
        )

        const dadosDistribuicaoDespesas =
          await getExpenseDistribution()

        setDistribuicaoDespesas(
          dadosDistribuicaoDespesas
        )

        const dadosOrcamentos =
          await getBudgetPipeline()

        setOrcamentos(
          dadosOrcamentos
        )

        const dadosHistorico =
          await getHistory()

        setHistorico(
          dadosHistorico
        )
      } catch (error) {
  console.error(
    'Erro ao carregar dashboard:',
    error
  )

  if (error instanceof Error) {
    setErro(error.message)
  } else {
    setErro(
      'Não foi possível carregar os dados financeiros.'
    )
  }
} finally {
  setCarregando(false)
}
    }

    carregarDashboard()
  }, [])
  async function aplicarFiltrosHistorico() {

  if (
    periodoInicial &&
    periodoFinal &&
    periodoInicial > periodoFinal
  ) {
    setErroHistorico(
      'O período inicial não pode ser maior que o período final.'
    )

    return
  }

  try {
    setCarregandoHistorico(true)
    setErroHistorico(null)

    const dadosFiltrados =
      await getHistory(
        periodoInicial || undefined,
        periodoFinal || undefined
      )

    setHistorico(
      dadosFiltrados
    )
  } catch (error) {

    console.error(
      'Erro ao filtrar histórico:',
      error
    )

    setErroHistorico(
      'Não foi possível aplicar os filtros.'
    )
  } finally {
    setCarregandoHistorico(false)
  }
}

  const formatarMoeda = (
    valor: number
  ) => {
    return valor.toLocaleString(
      'pt-BR',
      {
        style: 'currency',
        currency: 'BRL',
      }
    )
  }

  const formatarPercentual = (
    valor: number
  ) => {
    return valor.toLocaleString(
      'pt-BR',
      {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }
    )
  }

  if (carregando) {
    return (
      <p>
        Carregando dados...
      </p>
    )
  }

  if (erro) {
    return (
      <p>
        {erro}
      </p>
    )
  }

  if (!dados) {
    return (
      <p>
        Nenhum dado disponível.
      </p>
    )
  }

  const totalCentrosCusto =
    centrosCusto.reduce(
      (soma, centro) =>
        soma + centro.valor,
      0
    )

  const totalDistribuicaoDespesas =
    distribuicaoDespesas.reduce(
      (soma, despesa) =>
        soma + despesa.valor,
      0
    )

  const valorTotalOrcamentos =
    orcamentos.reduce(
      (soma, item) =>
        soma + item.valor,
      0
    )

  const valorTotalPonderado =
    orcamentos.reduce(
      (soma, item) =>
        soma + item.valorPonderado,
      0
    )

  return (
    <div id="dashboard" className="dashboard">

      {/* CABEÇALHO */}

      <header className="dashboard-header">

        <div>
          <h1>
            FinVista
          </h1>

          <p>
            Visão geral financeira
          </p>
        </div>

      </header>


      {/* INDICADORES PRINCIPAIS */}

      <section className="cards-grid">

        <article className="card">

          <span className="card-label">
            Receita
          </span>

          <strong className="card-value receita">
            {formatarMoeda(
              dados.receita
            )}
          </strong>

          <span className="card-description">
            Receita total do período
          </span>

        </article>


        <article className="card">

          <span className="card-label">
            Despesa
          </span>

          <strong className="card-value despesa">
            {formatarMoeda(
              dados.despesa
            )}
          </strong>

          <span className="card-description">
            Despesas totais do período
          </span>

        </article>


        <article className="card">

          <span className="card-label">
            Resultado
          </span>

          <strong className="card-value resultado">
            {formatarMoeda(
              dados.resultado
            )}
          </strong>

          <span className="card-description">
            Receita menos despesas
          </span>

        </article>


        <article className="card">

          <span className="card-label">
            Margem
          </span>

          <strong className="card-value margem">
            {formatarPercentual(
              dados.margem
            )}
            %
          </strong>

          <span className="card-description">
            Margem operacional
          </span>

        </article>

      </section>


      {/* GRÁFICOS PRINCIPAIS */}

      <section className="charts-grid">

        <RevenueExpenseChart
          receita={dados.receita}
          despesa={dados.despesa}
        />

        <ResultMarginChart
          resultado={dados.resultado}
          margem={dados.margem}
        />

      </section>

     {/* PROJEÇÃO DE 6 MESES */}

<section id="projecao" className="projection-section">

  <ProjectionChart
    dados={projecao}
  />

</section>


<section className="projection-table-card">

  <div className="projection-table-header">

    <div>
      <span className="projection-table-eyebrow">
        DETALHAMENTO FINANCEIRO
      </span>

      <h2>
        Resumo da projeção
      </h2>

      <p>
        Evolução mensal das receitas, despesas,
        resultados e saldo acumulado projetado.
      </p>
    </div>

    <div className="projection-table-summary">
      <span>Saldo ao final do período</span>

      <strong>
        {projecao.length > 0
          ? formatarMoeda(
              projecao[
                projecao.length - 1
              ].saldo
            )
          : formatarMoeda(0)}
      </strong>

      <small>
        {projecao.length} meses projetados
      </small>
    </div>

  </div>

  {projecao.length > 0 ? (

    <div className="table-wrapper">

      <table className="projection-table">

        <thead>
          <tr>
            <th>
              Mês
            </th>

            <th>
              Receita
            </th>

            <th>
              Despesa
            </th>

            <th>
              Resultado
            </th>

            <th>
              Saldo projetado
            </th>
          </tr>
        </thead>

        <tbody>

          {projecao.map(
            (item, index) => {

              const resultado =
                Number(item.resultado)

              const positivo =
                resultado >= 0

              const ultimoMes =
                index ===
                projecao.length - 1

              return (

                <tr
                  key={item.mes}
                  className={
                    ultimoMes
                      ? 'projection-table-final-row'
                      : ''
                  }
                >

                  <td>

                    <div className="projection-month">

                      <span className="projection-month-icon">
                        {index + 1}
                      </span>

                      <div>
                        <strong>
                          {item.mes}
                        </strong>

                        <small>
                          Mês projetado
                        </small>
                      </div>

                    </div>

                  </td>

                  <td>

                    <div className="projection-value">

                      <strong className="projection-revenue-value">
                        {formatarMoeda(
                          item.receita
                        )}
                      </strong>

                      <small>
                        Entrada prevista
                      </small>

                    </div>

                  </td>

                  <td>

                    <div className="projection-value">

                      <strong className="projection-expense-value">
                        {formatarMoeda(
                          item.despesa
                        )}
                      </strong>

                      <small>
                        Saída prevista
                      </small>

                    </div>

                  </td>

                  <td>

                    <span
                      className={
                        positivo
                          ? 'projection-result-badge projection-result-positive'
                          : 'projection-result-badge projection-result-negative'
                      }
                    >
                      <span className="projection-result-dot" />

                      {formatarMoeda(
                        resultado
                      )}
                    </span>

                  </td>

                  <td>

                    <div className="projection-balance-value">

                      <strong>
                        {formatarMoeda(
                          item.saldo
                        )}
                      </strong>

                      {ultimoMes && (
                        <span className="projection-final-badge">
                          Saldo final
                        </span>
                      )}

                    </div>

                  </td>

                </tr>

              )
            }
          )}

        </tbody>

      </table>

    </div>

  ) : (

    <div className="projection-table-empty">
      Nenhuma projeção financeira disponível.
    </div>

  )}

</section>


      {/* FLUXO DE CAIXA */}

      <section id="fluxo-caixa" className="cashflow-section">

  <CashFlowChart
    dados={fluxoCaixa}
  />

</section>


      <section className="cashflow-table-card">

        <h2>
          Detalhamento do fluxo de caixa
        </h2>

        <div className="table-wrapper">

          <table className="cashflow-table">

            <thead>

              <tr>

                <th>
                  Mês
                </th>

                <th>
                  Saldo inicial
                </th>

                <th>
                  Entradas
                </th>

                <th>
                  Saídas
                </th>

                <th>
                  Saldo final
                </th>

              </tr>

            </thead>

            <tbody>

              {fluxoCaixa.map(
                (item) => (

                  <tr key={item.mes}>

                    <td>
                      {item.mes}
                    </td>

                    <td>
                      {formatarMoeda(
                        item.saldoInicial
                      )}
                    </td>

                    <td>
                      {formatarMoeda(
                        item.entradas
                      )}
                    </td>

                    <td>
                      {formatarMoeda(
                        item.saidas
                      )}
                    </td>

                    <td>
                      {formatarMoeda(
                        item.saldoFinal
                      )}
                    </td>

                  </tr>

                )
              )}

            </tbody>

          </table>

        </div>

      </section>


      {/* CENTROS DE CUSTO */}

      <section id="centros-custo" className="cost-center-section">

  <CostCenterChart
    dados={centrosCusto}
  />

</section>


      <section className="cost-center-table-card">
  <div className="cost-center-table-header">
    <div>
      <span className="cost-center-table-eyebrow">
        DETALHAMENTO
      </span>

      <h2>
        Detalhamento por centro de custo
      </h2>

      <p>
        Ranking das áreas por participação
        nas despesas totais.
      </p>
    </div>

    <div className="cost-center-table-total">
      <span>
        Total analisado
      </span>

      <strong>
        {formatarMoeda(totalCentrosCusto)}
      </strong>
    </div>
  </div>

  <div className="table-wrapper">
    <table className="cost-center-table">
      <thead>
        <tr>
          <th className="cost-center-rank-column">
            #
          </th>

          <th>
            Centro de custo
          </th>

          <th>
            Valor
          </th>

          <th>
            Participação
          </th>
        </tr>
      </thead>

      <tbody>
        {centrosCusto.map(
          (item, index) => {
            const percentual =
              totalCentrosCusto > 0
                ? (
                    Number(item.valor) /
                    totalCentrosCusto
                  ) * 100
                : 0

            return (
              <tr key={item.nome}>
                <td className="cost-center-rank-column">
                  <span
                    className={
                      index === 0
                        ? 'cost-center-rank cost-center-rank-first'
                        : 'cost-center-rank'
                    }
                  >
                    {index + 1}
                  </span>
                </td>

                <td>
                  <div className="cost-center-name">
                    <span className="cost-center-name-icon">
                      {item.nome
                        .charAt(0)
                        .toUpperCase()}
                    </span>

                    <div>
                      <strong>
                        {item.nome}
                      </strong>

                      <small>
                        Centro de custo
                      </small>
                    </div>
                  </div>
                </td>

                <td>
                  <strong className="cost-center-value">
                    {formatarMoeda(
                      Number(item.valor)
                    )}
                  </strong>
                </td>

                <td>
                  <div className="cost-center-participation">
                    <div className="cost-center-participation-top">
                      <strong>
                        {formatarPercentual(
                          percentual
                        )}
                        %
                      </strong>

                      {index === 0 && (
                        <span className="cost-center-leading-badge">
                          Maior impacto
                        </span>
                      )}
                    </div>

                    <div
                      className="cost-center-progress"
                      role="progressbar"
                      aria-label={`Participação de ${item.nome}`}
                      aria-valuenow={Number(
                        percentual.toFixed(2)
                      )}
                      aria-valuemin={0}
                      aria-valuemax={100}
                    >
                      <div
                        className="cost-center-progress-fill"
                        style={{
                          width: `${Math.min(
                            percentual,
                            100
                          )}%`,
                        }}
                      />
                    </div>
                  </div>
                </td>
              </tr>
            )
          }
        )}
      </tbody>
    </table>
  </div>

  {centrosCusto.length === 0 && (
    <div className="cost-center-table-empty">
      Nenhum centro de custo disponível.
    </div>
  )}
</section>


      {/* DISTRIBUIÇÃO DAS DESPESAS */}

      <section id="despesas" className="expense-distribution-section">

        <ExpenseDistributionChart
          dados={
            distribuicaoDespesas
          }
        />

      </section>


      <section className="expense-distribution-table-card">

        <h2>
          Detalhamento das despesas
        </h2>

        <div className="table-wrapper">

          <table className="expense-distribution-table">

            <thead>

              <tr>

                <th>
                  Categoria
                </th>

                <th>
                  Valor
                </th>

                <th>
                  Participação
                </th>

              </tr>

            </thead>

            <tbody>

              {distribuicaoDespesas.map(
                (item) => {

                  const percentual =
                    totalDistribuicaoDespesas > 0
                      ? (
                          item.valor /
                          totalDistribuicaoDespesas
                        ) * 100
                      : 0

                  return (
                    <tr
                      key={item.categoria}
                    >

                      <td>
                        {item.categoria}
                      </td>

                      <td>
                        {formatarMoeda(
                          item.valor
                        )}
                      </td>

                      <td>
                        {formatarPercentual(
                          percentual
                        )}
                        %
                      </td>

                    </tr>
                  )
                }
              )}

            </tbody>

          </table>

        </div>

      </section>


      {/* ORÇAMENTOS / PIPELINE */}

      <section id="orcamentos" className="budget-pipeline-section">

        <div className="pipeline-summary">

          <div className="pipeline-summary-card">

            <span>
              Pipeline total
            </span>

            <strong>
              {formatarMoeda(
                valorTotalOrcamentos
              )}
            </strong>

          </div>


          <div className="pipeline-summary-card">

            <span>
              Receita ponderada
            </span>

            <strong>
              {formatarMoeda(
                valorTotalPonderado
              )}
            </strong>

          </div>

        </div>


        <BudgetPipelineChart
          dados={orcamentos}
        />

      </section>


      <section className="budget-pipeline-table-card">

  <div className="budget-pipeline-table-header">

    <div>
      <span className="budget-pipeline-table-eyebrow">
        OPORTUNIDADES
      </span>

      <h2>
        Oportunidades comerciais
      </h2>

      <p>
        Acompanhamento das propostas por valor,
        estágio e probabilidade de fechamento.
      </p>
    </div>

    <div className="budget-pipeline-table-total">
      <span>Pipeline total</span>

      <strong>
        {formatarMoeda(
          orcamentos.reduce(
            (total, item) =>
              total + Number(item.valor),
            0,
          ),
        )}
      </strong>

      <small>
        {orcamentos.length} oportunidades
      </small>
    </div>

  </div>

  {orcamentos.length > 0 ? (

    <div className="table-wrapper">

      <table className="budget-pipeline-table">

        <thead>
          <tr>
            <th className="budget-rank-column">
              #
            </th>

            <th>
              Cliente
            </th>

            <th>
              Valor
            </th>

            <th>
              Status
            </th>

            <th>
              Probabilidade
            </th>

            <th>
              Receita ponderada
            </th>
          </tr>
        </thead>

        <tbody>

          {orcamentos.map(
            (item, index) => {

              const probabilidade =
                Number(item.probabilidade)

              const statusClass =
                item.status === 'Negociação'
                  ? 'budget-status-negotiation'
                  : item.status === 'Proposta enviada'
                    ? 'budget-status-proposal'
                    : item.status === 'Em análise'
                      ? 'budget-status-analysis'
                      : 'budget-status-initial'

              return (

                <tr key={item.id}>

                  <td className="budget-rank-column">

                    <span
                      className={
                        index === 0
                          ? 'budget-rank budget-rank-first'
                          : 'budget-rank'
                      }
                    >
                      {index + 1}
                    </span>

                  </td>

                  <td>

                    <div className="budget-client">

                      <span className="budget-client-icon">
                        {item.cliente
                          .charAt(0)
                          .toUpperCase()}
                      </span>

                      <div>
                        <strong>
                          {item.cliente}
                        </strong>

                        <small>
                          Oportunidade comercial
                        </small>
                      </div>

                    </div>

                  </td>

                  <td>

                    <strong className="budget-value">
                      {formatarMoeda(
                        Number(item.valor),
                      )}
                    </strong>

                  </td>

                  <td>

                    <span
                      className={`budget-status ${statusClass}`}
                    >
                      <span className="budget-status-dot" />

                      {item.status}
                    </span>

                  </td>

                  <td>

                    <div className="budget-probability">

                      <div className="budget-probability-top">

                        <strong>
                          {probabilidade}%
                        </strong>

                        {probabilidade >= 80 && (
                          <span className="budget-high-chance">
                            Alta chance
                          </span>
                        )}

                      </div>

                      <div
                        className="budget-progress"
                        role="progressbar"
                        aria-label={`Probabilidade de fechamento de ${item.cliente}`}
                        aria-valuenow={probabilidade}
                        aria-valuemin={0}
                        aria-valuemax={100}
                      >

                        <div
                          className="budget-progress-fill"
                          style={{
                            width: `${Math.min(
                              Math.max(
                                probabilidade,
                                0,
                              ),
                              100,
                            )}%`,
                          }}
                        />

                      </div>

                    </div>

                  </td>

                  <td>

                    <div className="budget-weighted-value">

                      <strong>
                        {formatarMoeda(
                          Number(
                            item.valorPonderado,
                          ),
                        )}
                      </strong>

                      <small>
                        Valor ponderado
                      </small>

                    </div>

                  </td>

                </tr>
              )
            },
          )}

        </tbody>

      </table>

    </div>

  ) : (

    <div className="budget-pipeline-table-empty">
      Nenhuma oportunidade comercial disponível.
    </div>

  )}

</section>


      {/* HISTÓRICO E FILTROS */}

     <section id="historico" className="history-section">

  <div className="history-filters">

    <div className="filter-group">

      <label htmlFor="periodo-inicial">
        Período inicial
      </label>

      <input
        id="periodo-inicial"
        type="month"
        value={periodoInicial}
        onChange={(event) =>
          setPeriodoInicial(
            event.target.value
          )
        }
      />

    </div>


    <div className="filter-group">

      <label htmlFor="periodo-final">
        Período final
      </label>

      <input
        id="periodo-final"
        type="month"
        value={periodoFinal}
        onChange={(event) =>
          setPeriodoFinal(
            event.target.value
          )
        }
      />

    </div>


    <button
      type="button"
      className="filter-button"
      onClick={
        aplicarFiltrosHistorico
      }
      disabled={
        carregandoHistorico
      }
    >
      {carregandoHistorico
        ? 'Filtrando...'
        : 'Aplicar filtros'}
    </button>

  </div>


  {erroHistorico && (
    <p className="history-error">
      {erroHistorico}
    </p>
  )}


  <HistoryChart
    dados={historico}
  />

</section>

    </div>
  )
}

export default Dashboard