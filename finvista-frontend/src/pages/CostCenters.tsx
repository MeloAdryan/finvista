import { useEffect, useState } from 'react'

import {
  getCostCenters,
  type CostCenterData,
} from '../services/costCenterService'

import CostCenterChart from '../charts/CostCenterChart'

import '../styles/dashboard.css'

function CostCenters() {
  const [centrosCusto, setCentrosCusto] =
    useState<CostCenterData[]>([])

  const [carregando, setCarregando] =
    useState(true)

  const [erro, setErro] =
    useState<string | null>(null)

  useEffect(() => {
    async function carregarCentrosCusto() {
      try {
        setCarregando(true)
        setErro(null)

        const dadosCentrosCusto =
          await getCostCenters()

        setCentrosCusto(dadosCentrosCusto)
      } catch (error) {
        console.error(
          'Erro ao carregar centros de custo:',
          error,
        )

        if (error instanceof Error) {
          setErro(error.message)
        } else {
          setErro(
            'Não foi possível carregar os centros de custo.',
          )
        }
      } finally {
        setCarregando(false)
      }
    }

    void carregarCentrosCusto()
  }, [])

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    })
  }

  const formatarPercentual = (valor: number) => {
    return valor.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
  }

  if (carregando) {
    return <p>Carregando centros de custo...</p>
  }

  if (erro) {
    return <p>{erro}</p>
  }

  const totalCentrosCusto =
    centrosCusto.reduce(
      (soma, centro) =>
        soma + Number(centro.valor),
      0,
    )

  return (
    <div className="dashboard">
      <section
        id="centros-custo"
        className="cost-center-section"
      >
        <CostCenterChart dados={centrosCusto} />
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
            <span>Total analisado</span>

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

                <th>Centro de custo</th>
                <th>Valor</th>
                <th>Participação</th>
              </tr>
            </thead>

            <tbody>
              {centrosCusto.map((item, index) => {
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
                          Number(item.valor),
                        )}
                      </strong>
                    </td>

                    <td>
                      <div className="cost-center-participation">
                        <div className="cost-center-participation-top">
                          <strong>
                            {formatarPercentual(
                              percentual,
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
                            percentual.toFixed(2),
                          )}
                          aria-valuemin={0}
                          aria-valuemax={100}
                        >
                          <div
                            className="cost-center-progress-fill"
                            style={{
                              width: `${Math.min(
                                percentual,
                                100,
                              )}%`,
                            }}
                          />
                        </div>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {centrosCusto.length === 0 && (
          <div className="cost-center-table-empty">
            Nenhum centro de custo disponível.
          </div>
        )}
      </section>
    </div>
  )
}

export default CostCenters