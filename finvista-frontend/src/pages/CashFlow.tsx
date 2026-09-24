import { useEffect, useState } from 'react'

import {
  getCashFlow,
  type CashFlowData,
} from '../services/cashFlowService'

import CashFlowChart from '../charts/CashFlowChart'

import '../styles/dashboard.css'

function CashFlow() {
  const [fluxoCaixa, setFluxoCaixa] =
    useState<CashFlowData[]>([])

  const [carregando, setCarregando] =
    useState(true)

  const [erro, setErro] =
    useState<string | null>(null)

  useEffect(() => {
    async function carregarFluxoCaixa() {
      try {
        setCarregando(true)
        setErro(null)

        const dadosFluxoCaixa =
          await getCashFlow()

        setFluxoCaixa(dadosFluxoCaixa)
      } catch (error) {
        console.error(
          'Erro ao carregar fluxo de caixa:',
          error,
        )

        if (error instanceof Error) {
          setErro(error.message)
        } else {
          setErro(
            'Não foi possível carregar o fluxo de caixa.',
          )
        }
      } finally {
        setCarregando(false)
      }
    }

    void carregarFluxoCaixa()
  }, [])

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    })
  }

  if (carregando) {
    return <p>Carregando fluxo de caixa...</p>
  }

  if (erro) {
    return <p>{erro}</p>
  }

  return (
    <div className="dashboard">
      <section
        id="fluxo-caixa"
        className="cashflow-section"
      >
        <CashFlowChart dados={fluxoCaixa} />
      </section>

      <section className="cashflow-table-card">
        <h2>Detalhamento do fluxo de caixa</h2>

        <div className="table-wrapper">
          <table className="cashflow-table">
            <thead>
              <tr>
                <th>Mês</th>
                <th>Saldo inicial</th>
                <th>Entradas</th>
                <th>Saídas</th>
                <th>Saldo final</th>
              </tr>
            </thead>

            <tbody>
              {fluxoCaixa.map((item) => (
                <tr key={item.mes}>
                  <td>{item.mes}</td>

                  <td>
                    {formatarMoeda(
                      item.saldoInicial,
                    )}
                  </td>

                  <td>
                    {formatarMoeda(
                      item.entradas,
                    )}
                  </td>

                  <td>
                    {formatarMoeda(
                      item.saidas,
                    )}
                  </td>

                  <td>
                    {formatarMoeda(
                      item.saldoFinal,
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

export default CashFlow