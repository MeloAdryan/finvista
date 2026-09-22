import {
  useEffect,
  useMemo,
  useState,
} from 'react'

import type { FormEvent } from 'react'

import {
  criarMeta,
  listarMetas,
} from '../services/spendingGoalService'

import type {
  CreateSpendingGoalRequest,
  SpendingGoal,
} from '../services/spendingGoalService'

import '../styles/spending-goals.css'

const formatarMoeda = (valor: number) =>
  new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(valor || 0)

const formatarData = (data: string) => {
  if (!data) {
    return '-'
  }

  return new Intl.DateTimeFormat('pt-BR').format(
    new Date(`${data}T00:00:00`),
  )
}

const classeStatus = (status: string) =>
  status.toLowerCase()

function SpendingGoals() {
  const [metas, setMetas] = useState<SpendingGoal[]>([])
  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')
  const [mostrarFormulario, setMostrarFormulario] =
    useState(false)

  const [formulario, setFormulario] =
    useState<CreateSpendingGoalRequest>({
      tipo: 'MENSAL',
      dataInicio: '',
      dataFim: '',
      valorLimite: 0,
      percentualAlerta: 80,
    })

  const carregarMetas = async () => {
    try {
      setCarregando(true)
      setErro('')

      const dados = await listarMetas()
      setMetas(dados)
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : 'Não foi possível carregar as metas.',
      )
    } finally {
      setCarregando(false)
    }
  }

useEffect(() => {
  let ativo = true

  listarMetas()
    .then((dados) => {
      if (ativo) {
        setMetas(dados)
        setErro('')
      }
    })
    .catch((error) => {
      if (ativo) {
        setErro(
          error instanceof Error
            ? error.message
            : 'Não foi possível carregar as metas.',
        )
      }
    })
    .finally(() => {
      if (ativo) {
        setCarregando(false)
      }
    })

  return () => {
    ativo = false
  }
}, [])

  const resumo = useMemo(() => {
    const limiteTotal = metas.reduce(
      (total, meta) => total + Number(meta.valorLimite),
      0,
    )

    const gastoTotal = metas.reduce(
      (total, meta) => total + Number(meta.gastoAtual),
      0,
    )

    const saldoTotal = metas.reduce(
      (total, meta) => total + Number(meta.saldoMeta),
      0,
    )

    const metasAtencao = metas.filter(
      (meta) =>
        meta.status === 'ALERTA' ||
        meta.status === 'EXCEDIDA',
    ).length

    return {
      limiteTotal,
      gastoTotal,
      saldoTotal,
      metasAtencao,
    }
  }, [metas])

  const atualizarFormulario = (
    campo: keyof CreateSpendingGoalRequest,
    valor: string | number,
  ) => {
    setFormulario((atual) => ({
      ...atual,
      [campo]: valor,
    }))
  }

  const salvarMeta = async (event: FormEvent) => {
    event.preventDefault()

    if (
      !formulario.dataInicio ||
      !formulario.dataFim ||
      formulario.valorLimite <= 0
    ) {
      setErro(
        'Preencha o período e informe um valor limite maior que zero.',
      )
      return
    }

    if (
      formulario.percentualAlerta <= 0 ||
      formulario.percentualAlerta > 100
    ) {
      setErro(
        'O percentual de alerta deve estar entre 1% e 100%.',
      )
      return
    }

    if (formulario.dataFim < formulario.dataInicio) {
      setErro(
        'A data final não pode ser anterior à data inicial.',
      )
      return
    }

    try {
      setSalvando(true)
      setErro('')

      await criarMeta(formulario)

      setFormulario({
        tipo: 'MENSAL',
        dataInicio: '',
        dataFim: '',
        valorLimite: 0,
        percentualAlerta: 80,
      })

      setMostrarFormulario(false)

      await carregarMetas()
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : 'Não foi possível criar a meta.',
      )
    } finally {
      setSalvando(false)
    }
  }

  return (
    <section className="metas-page">
      <div className="metas-header">
        <div>
          <span className="metas-eyebrow">
            PLANEJAMENTO FINANCEIRO
          </span>

          <h2>Metas financeiras</h2>

          <p>
            Defina limites de gastos, acompanhe o consumo
            do orçamento e identifique riscos antes que
            os limites sejam ultrapassados.
          </p>
        </div>

        <button
          type="button"
          className="metas-primary-button"
          onClick={() =>
            setMostrarFormulario((atual) => !atual)
          }
        >
          <span>+</span>
          Nova meta
        </button>
      </div>

      {erro && (
        <div className="metas-alerta-erro">
          {erro}
        </div>
      )}

      {mostrarFormulario && (
        <form
          className="metas-formulario"
          onSubmit={salvarMeta}
        >
          <div className="metas-formulario-header">
            <div>
              <span>CONFIGURAÇÃO</span>
              <h3>Nova meta de gastos</h3>
            </div>

            <button
              type="button"
              className="metas-fechar"
              onClick={() => setMostrarFormulario(false)}
              aria-label="Fechar formulário"
            >
              ×
            </button>
          </div>

          <div className="metas-form-grid">
            <label>
              Tipo da meta
              <select
                value={formulario.tipo}
                onChange={(event) =>
                  atualizarFormulario(
                    'tipo',
                    event.target.value,
                  )
                }
              >
                <option value="MENSAL">Mensal</option>
                <option value="SEMESTRAL">
                  Semestral
                </option>
              </select>
            </label>

            <label>
              Valor limite
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={
                  formulario.valorLimite || ''
                }
                onChange={(event) =>
                  atualizarFormulario(
                    'valorLimite',
                    Number(event.target.value),
                  )
                }
                placeholder="Ex.: 100000,00"
              />
            </label>

            <label>
              Data inicial
              <input
                type="date"
                value={formulario.dataInicio}
                onChange={(event) =>
                  atualizarFormulario(
                    'dataInicio',
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Data final
              <input
                type="date"
                value={formulario.dataFim}
                onChange={(event) =>
                  atualizarFormulario(
                    'dataFim',
                    event.target.value,
                  )
                }
              />
            </label>

            <label>
              Alerta a partir de
              <div className="metas-input-percentual">
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={formulario.percentualAlerta}
                  onChange={(event) =>
                    atualizarFormulario(
                      'percentualAlerta',
                      Number(event.target.value),
                    )
                  }
                />
                <span>%</span>
              </div>
            </label>
          </div>

          <div className="metas-formulario-footer">
            <span>
              O alerta não bloqueia lançamentos. Ele
              funciona como indicador gerencial.
            </span>

            <div>
              <button
                type="button"
                className="metas-secondary-button"
                onClick={() =>
                  setMostrarFormulario(false)
                }
              >
                Cancelar
              </button>

              <button
                type="submit"
                className="metas-primary-button"
                disabled={salvando}
              >
                {salvando
                  ? 'Salvando...'
                  : 'Criar meta'}
              </button>
            </div>
          </div>
        </form>
      )}

      <div className="metas-resumo">
        <article>
          <span>Limite planejado</span>
          <strong>
            {formatarMoeda(resumo.limiteTotal)}
          </strong>
          <small>
            Soma das metas cadastradas
          </small>
        </article>

        <article>
          <span>Gastos acumulados</span>
          <strong>
            {formatarMoeda(resumo.gastoTotal)}
          </strong>
          <small>
            Valor consumido nas metas
          </small>
        </article>

        <article>
          <span>Saldo disponível</span>
          <strong>
            {formatarMoeda(resumo.saldoTotal)}
          </strong>
          <small>
            Margem restante planejada
          </small>
        </article>

        <article>
          <span>Requer atenção</span>
          <strong>{resumo.metasAtencao}</strong>
          <small>
            Metas em alerta ou excedidas
          </small>
        </article>
      </div>

      <div className="metas-content">
        <div className="metas-section-heading">
          <div>
            <span>ACOMPANHAMENTO</span>
            <h3>Metas cadastradas</h3>
          </div>

          <strong>{metas.length}</strong>
        </div>

        {carregando ? (
          <div className="metas-estado">
            Carregando metas...
          </div>
        ) : metas.length === 0 ? (
          <div className="metas-vazio">
            <div className="metas-vazio-icon">◎</div>

            <h3>Nenhuma meta cadastrada</h3>

            <p>
              Crie a primeira meta financeira para
              começar a acompanhar limites e gastos.
            </p>

            <button
              type="button"
              className="metas-primary-button"
              onClick={() =>
                setMostrarFormulario(true)
              }
            >
              + Criar primeira meta
            </button>
          </div>
        ) : (
          <div className="metas-lista">
            {metas.map((meta) => {
              const percentual =
                Number(meta.percentualUtilizado) || 0

              const larguraBarra = Math.min(
                Math.max(percentual, 0),
                100,
              )

              return (
                <article
                  className="meta-card"
                  key={meta.id}
                >
                  <div className="meta-card-top">
                    <div>
                      <span className="meta-tipo">
                        {meta.tipo}
                      </span>

                      <h3>
                        Meta de gastos #{meta.id}
                      </h3>

                      <small>
                        {formatarData(meta.dataInicio)}
                        {' — '}
                        {formatarData(meta.dataFim)}
                      </small>
                    </div>

                    <span
                      className={`meta-status meta-status-${classeStatus(
                        meta.status,
                      )}`}
                    >
                      {meta.status}
                    </span>
                  </div>

                  <div className="meta-valores">
                    <div>
                      <span>Limite</span>
                      <strong>
                        {formatarMoeda(
                          Number(meta.valorLimite),
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Utilizado</span>
                      <strong>
                        {formatarMoeda(
                          Number(meta.gastoAtual),
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Saldo</span>
                      <strong>
                        {formatarMoeda(
                          Number(meta.saldoMeta),
                        )}
                      </strong>
                    </div>
                  </div>

                  <div className="meta-progresso-header">
                    <span>Utilização da meta</span>

                    <strong>
                      {percentual.toFixed(1)}%
                    </strong>
                  </div>

                  <div className="meta-progresso">
                    <div
                      className={`meta-progresso-barra meta-progresso-${classeStatus(
                        meta.status,
                      )}`}
                      style={{
                        width: `${larguraBarra}%`,
                      }}
                    />
                  </div>

                  <div className="meta-card-footer">
                    <span>
                      Alerta configurado em{' '}
                      <strong>
                        {meta.percentualAlerta}%
                      </strong>
                    </span>

                    {meta.status === 'NORMAL' && (
                      <span>
                        Dentro do planejamento
                      </span>
                    )}

                    {meta.status === 'ALERTA' && (
                      <span>
                        Limite próximo de ser atingido
                      </span>
                    )}

                    {meta.status === 'EXCEDIDA' && (
                      <span>
                        Limite planejado ultrapassado
                      </span>
                    )}
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}

export default SpendingGoals