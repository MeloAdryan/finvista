import { useEffect, useState } from 'react'

import {
  getProjection,
  type ProjectionData,
} from '../services/projectionService'

import ProjectionChart from '../charts/ProjectionChart'

import '../styles/dashboard.css'

function Projection() {
  const [projecao, setProjecao] =
    useState<ProjectionData[]>([])

  const [carregando, setCarregando] =
    useState(true)

  const [erro, setErro] =
    useState<string | null>(null)

  useEffect(() => {
    async function carregarProjecao() {
      try {
        const dadosProjecao =
          await getProjection()

        setProjecao(dadosProjecao)
      } catch (error) {
        console.error(
          'Erro ao carregar projeção:',
          error,
        )

        if (error instanceof Error) {
          setErro(error.message)
        } else {
          setErro(
            'Não foi possível carregar a projeção financeira.',
          )
        }
      } finally {
        setCarregando(false)
      }
    }

    void carregarProjecao()
  }, [])

  const formatarMoeda = (
    valor: number,
  ) => {
    return valor.toLocaleString(
      'pt-BR',
      {
        style: 'currency',
        currency: 'BRL',
      },
    )
  }

  if (carregando) {
    return (
      <p>Carregando projeção...</p>
    )
  }

  if (erro) {
    return (
      <p>{erro}</p>
    )
  }

  return (
    <div className="dashboard">
      <section
        id="projecao"
        className="projection-section"
      >
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
              Evolução mensal das receitas,
              despesas, resultados e saldo
              acumulado projetado.
            </p>
          </div>

          <div className="projection-table-summary">
            <span>
              Saldo ao final do período
            </span>

            <strong>
              {projecao.length > 0
                ? formatarMoeda(
                    projecao[
                      projecao.length - 1
                    ].saldo,
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
                  <th>Mês</th>
                  <th>Receita</th>
                  <th>Despesa</th>
                  <th>Resultado</th>
                  <th>Saldo projetado</th>
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
                                item.receita,
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
                                item.despesa,
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
                              resultado,
                            )}
                          </span>
                        </td>

                        <td>
                          <div className="projection-balance-value">
                            <strong>
                              {formatarMoeda(
                                item.saldo,
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
                  },
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
    </div>
  )
}

export default Projection