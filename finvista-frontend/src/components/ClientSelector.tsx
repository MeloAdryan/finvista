import { useEffect, useState } from 'react'

import {
  listarClientes,
  type Cliente,
} from '../services/clientService'

import '../styles/client-selector.css'

interface ClientSelectorProps {
  onSelecionar: (cliente: Cliente) => Promise<void>
}

function ClientSelector({
  onSelecionar,
}: ClientSelectorProps) {
  const [clientes, setClientes] = useState<Cliente[]>([])
  const [carregando, setCarregando] = useState(true)

  const [selecionandoId, setSelecionandoId] =
    useState<number | null>(null)

  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true

    const carregarClientes = async () => {
      try {
        setCarregando(true)
        setErro(null)

        const resposta = await listarClientes()

        if (ativo) {
          setClientes(resposta)
        }
      } catch (error) {
        console.error(
          'Erro ao carregar clientes:',
          error,
        )

        if (ativo) {
          setErro(
            error instanceof Error
              ? error.message
              : 'Não foi possível carregar os clientes.',
          )
        }
      } finally {
        if (ativo) {
          setCarregando(false)
        }
      }
    }

    void carregarClientes()

    return () => {
      ativo = false
    }
  }, [])

  const selecionar = async (cliente: Cliente) => {
    try {
      setSelecionandoId(cliente.id)
      setErro(null)

      await onSelecionar(cliente)
    } catch (error) {
      console.error(
        'Erro ao selecionar cliente:',
        error,
      )

      setErro(
        error instanceof Error
          ? error.message
          : 'Não foi possível selecionar o cliente.',
      )

      setSelecionandoId(null)
    }
  }

  const obterIniciais = (nome: string) => {
    const partes = nome
      .trim()
      .split(/\s+/)
      .filter(Boolean)

    if (partes.length === 0) {
      return 'C'
    }

    if (partes.length === 1) {
      return partes[0]
        .slice(0, 2)
        .toUpperCase()
    }

    return (
      partes[0].charAt(0) +
      partes[partes.length - 1].charAt(0)
    ).toUpperCase()
  }

  return (
    <main className="finvista-client-selector">
      <div className="finvista-client-selector-background">
        <span className="finvista-client-selector-orb finvista-client-selector-orb-one" />
        <span className="finvista-client-selector-orb finvista-client-selector-orb-two" />
      </div>

      <section className="finvista-client-selector-card">
        <div className="finvista-client-selector-brand">
          <div className="finvista-client-selector-logo">
            <span />
            <span />
            <span />
          </div>

          <div className="finvista-client-selector-brand-text">
            <strong>FinVista</strong>
            <span>Financial Intelligence</span>
          </div>
        </div>

        <div className="finvista-client-selector-heading">
          <span className="finvista-client-selector-eyebrow">
            ADMINISTRAÇÃO
          </span>

          <h1>Selecione um cliente</h1>

          <p>
            Escolha a empresa cujos dados financeiros
            você deseja administrar.
          </p>
        </div>

        <div className="finvista-client-selector-content">
          {carregando ? (
            <div className="finvista-client-selector-loading">
              <div className="finvista-client-selector-spinner" />

              <strong>Carregando clientes</strong>

              <span>
                Aguarde enquanto buscamos sua carteira.
              </span>
            </div>
          ) : erro ? (
            <div
              className="finvista-client-selector-error"
              role="alert"
            >
              <div className="finvista-client-selector-error-icon">
                !
              </div>

              <div>
                <strong>
                  Não foi possível carregar os clientes
                </strong>

                <span>{erro}</span>
              </div>
            </div>
          ) : clientes.length === 0 ? (
            <div className="finvista-client-selector-empty">
              <div className="finvista-client-selector-empty-icon">
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16" />
                  <path d="M16 8h2a2 2 0 0 1 2 2v11" />
                  <path d="M8 7h4" />
                  <path d="M8 11h4" />
                  <path d="M8 15h4" />
                  <path d="M2 21h20" />
                </svg>
              </div>

              <strong>
                Nenhum cliente disponível
              </strong>

              <span>
                Nenhum cliente ativo foi encontrado
                para esta conta.
              </span>
            </div>
          ) : (
            <>
              <div className="finvista-client-selector-list-header">
                <div>
                  <strong>
                    Clientes disponíveis
                  </strong>

                  <span>
                    Selecione uma empresa para acessar
                    o painel financeiro.
                  </span>
                </div>

                <span className="finvista-client-selector-count">
                  {clientes.length}
                </span>
              </div>

              <div className="finvista-client-selector-list">
                {clientes.map((cliente) => {
                  const selecionando =
                    selecionandoId === cliente.id

                  return (
                    <button
                      key={cliente.id}
                      type="button"
                      className={`finvista-client-selector-item ${
                        selecionando
                          ? 'finvista-client-selector-item-loading'
                          : ''
                      }`}
                      disabled={selecionandoId !== null}
                      onClick={() => {
                        void selecionar(cliente)
                      }}
                    >
                      <span className="finvista-client-selector-avatar">
                        {obterIniciais(cliente.nome)}
                      </span>

                      <span className="finvista-client-selector-info">
                        <strong>
                          {cliente.nome}
                        </strong>

                        <small>
                          <span className="finvista-client-selector-status-dot" />

                          Cliente ativo

                          <span className="finvista-client-selector-info-separator">
                            •
                          </span>

                          ID #{cliente.id}
                        </small>
                      </span>

                      <span className="finvista-client-selector-action">
                        {selecionando ? (
                          <>
                            <span className="finvista-client-selector-button-spinner" />
                            Abrindo...
                          </>
                        ) : (
                          <>
                            <span>Acessar</span>

                            <svg
                              viewBox="0 0 24 24"
                              aria-hidden="true"
                            >
                              <path d="M5 12H19" />
                              <path d="M13 6L19 12L13 18" />
                            </svg>
                          </>
                        )}
                      </span>
                    </button>
                  )
                })}
              </div>
            </>
          )}
        </div>

        <footer className="finvista-client-selector-footer">
          <span className="finvista-client-selector-footer-status">
            <i />

            Ambiente local
          </span>

          <span>
            FinVista © 2026
          </span>
        </footer>
      </section>
    </main>
  )
}

export default ClientSelector