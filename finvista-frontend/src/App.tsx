import {
  type MouseEvent,
  useEffect,
  useState,
} from 'react'

import Dashboard from './pages/Dashboard'
import SpendingGoals from './pages/SpendingGoals'
import ImportData from './pages/ImportData'
import Login from './pages/Login'

import {
  logout,
  obterUsuarioAtual,
  type AuthUser,
} from './services/authService'

import './styles/app-layout.css'

type PaginaAtiva =
  | 'dashboard'
  | 'metas'
  | 'importacao'

type SecaoDashboard =
  | 'dashboard'
  | 'projecao'
  | 'fluxo-caixa'
  | 'centros-custo'
  | 'despesas'
  | 'orcamentos'
  | 'historico'

function DashboardIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="2" />
      <rect x="14" y="3" width="7" height="7" rx="2" />
      <rect x="3" y="14" width="7" height="7" rx="2" />
      <rect x="14" y="14" width="7" height="7" rx="2" />
    </svg>
  )
}

function GoalsIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="4" />
      <circle cx="12" cy="12" r="1" />
      <path d="M15 9L20 4" />
      <path d="M17 4H20V7" />
    </svg>
  )
}

function ProjectionIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 18V6" />
      <path d="M4 18H20" />
      <path d="M7 14L11 10L14 13L20 7" />
      <path d="M16 7H20V11" />
    </svg>
  )
}

function CashFlowIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 7H18" />
      <path d="M15 4L18 7L15 10" />
      <path d="M17 17H6" />
      <path d="M9 14L6 17L9 20" />
    </svg>
  )
}

function CostCenterIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="4" />
      <circle cx="12" cy="12" r="1" />
    </svg>
  )
}

function ExpenseIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 19V11" />
      <path d="M10 19V5" />
      <path d="M16 19V8" />
      <path d="M22 19H2" />
    </svg>
  )
}

function BudgetIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 3H19V21L16 19L13 21L10 19L7 21L5 19V3Z" />
      <path d="M9 8H15" />
      <path d="M9 12H15" />
      <path d="M9 16H13" />
    </svg>
  )
}

function HistoryIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 12A9 9 0 1 0 6 5.3" />
      <path d="M3 4V10H9" />
      <path d="M12 7V12L15 14" />
    </svg>
  )
}

function ImportIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3V15" />
      <path d="M7 10L12 15L17 10" />
      <path d="M5 20H19" />
      <path d="M5 17V20" />
      <path d="M19 17V20" />
    </svg>
  )
}

function LogoutIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 5H5V19H10" />
      <path d="M14 8L18 12L14 16" />
      <path d="M18 12H9" />
    </svg>
  )
}

function App() {
  const [usuario, setUsuario] =
    useState<AuthUser | null>(null)

  const [verificandoSessao, setVerificandoSessao] =
    useState(true)

  const [paginaAtiva, setPaginaAtiva] =
    useState<PaginaAtiva>('metas')

  const [secaoAtiva, setSecaoAtiva] =
    useState('metas')

  const [sidebarRecolhida, setSidebarRecolhida] =
    useState(false)

  const [menuMobileAberto, setMenuMobileAberto] =
    useState(false)

  useEffect(() => {
    let ativo = true

    const verificarSessao = async () => {
      try {
        const usuarioAtual =
          await obterUsuarioAtual()

        if (ativo) {
          setUsuario(usuarioAtual)
        }
      } catch (error) {
        console.error(
          'Erro ao verificar sessão:',
          error,
        )

        if (ativo) {
          setUsuario(null)
        }
      } finally {
        if (ativo) {
          setVerificandoSessao(false)
        }
      }
    }

    void verificarSessao()

    return () => {
      ativo = false
    }
  }, [])

  useEffect(() => {
    if (paginaAtiva !== 'dashboard') {
      return
    }

    const ids: SecaoDashboard[] = [
      'dashboard',
      'projecao',
      'fluxo-caixa',
      'centros-custo',
      'despesas',
      'orcamentos',
      'historico',
    ]

    const atualizarSecaoAtiva = () => {
      const pontoDeLeitura = 190
      let secaoAtual: SecaoDashboard = 'dashboard'

      for (const id of ids) {
        const elemento =
          document.getElementById(id)

        if (!elemento) {
          continue
        }

        const topo =
          elemento.getBoundingClientRect().top

        if (topo <= pontoDeLeitura) {
          secaoAtual = id
        }
      }

      setSecaoAtiva(secaoAtual)
    }

    atualizarSecaoAtiva()

    window.addEventListener(
      'scroll',
      atualizarSecaoAtiva,
      { passive: true },
    )

    return () => {
      window.removeEventListener(
        'scroll',
        atualizarSecaoAtiva,
      )
    }
  }, [paginaAtiva])

  const fecharMenuMobile = () => {
    setMenuMobileAberto(false)
  }

  const abrirMetas = () => {
    setPaginaAtiva('metas')
    setSecaoAtiva('metas')
    fecharMenuMobile()

    window.scrollTo({
      top: 0,
      behavior: 'smooth',
    })
  }

  const abrirDashboard = () => {
    setPaginaAtiva('dashboard')
    setSecaoAtiva('dashboard')
    fecharMenuMobile()

    window.setTimeout(() => {
      document
        .getElementById('dashboard')
        ?.scrollIntoView({
          behavior: 'smooth',
          block: 'start',
        })
    }, 0)
  }

  const abrirSecaoDashboard = (
    secao: SecaoDashboard,
  ) => {
    setPaginaAtiva('dashboard')
    setSecaoAtiva(secao)
    fecharMenuMobile()

    window.setTimeout(() => {
      document
        .getElementById(secao)
        ?.scrollIntoView({
          behavior: 'smooth',
          block: 'start',
        })
    }, 0)
  }

  const navegarParaSecao = (
    event: MouseEvent<HTMLAnchorElement>,
    secao: SecaoDashboard,
  ) => {
    event.preventDefault()
    abrirSecaoDashboard(secao)
  }

  const abrirImportacao = () => {
    if (usuario?.perfil !== 'ADMIN') {
      abrirMetas()
      return
    }

    setPaginaAtiva('importacao')
    setSecaoAtiva('importacao')
    fecharMenuMobile()

    window.scrollTo({
      top: 0,
      behavior: 'smooth',
    })
  }

  const realizarLogout = async () => {
    try {
      await logout()
    } catch (error) {
      console.error(
        'Erro ao encerrar sessão:',
        error,
      )
    } finally {
      setUsuario(null)
      setPaginaAtiva('metas')
      setSecaoAtiva('metas')
      fecharMenuMobile()
    }
  }

  if (verificandoSessao) {
    return (
      <main className="finvista-auth-loading">
        <div className="finvista-auth-loading-brand">
          <strong>FinVista</strong>
          <span>Verificando acesso...</span>
        </div>
      </main>
    )
  }

  if (!usuario) {
    return (
      <Login
        onLogin={(usuarioAutenticado) => {
          setUsuario(usuarioAutenticado)
          setPaginaAtiva('metas')
          setSecaoAtiva('metas')
        }}
      />
    )
  }

  const usuarioAdmin =
    usuario.perfil === 'ADMIN'

  return (
    <div
      className={`finvista-app ${
        sidebarRecolhida
          ? 'finvista-app-sidebar-collapsed'
          : ''
      }`}
    >
      <aside
        className={`finvista-sidebar ${
          menuMobileAberto
            ? 'finvista-sidebar-mobile-open'
            : ''
        }`}
      >
        <div className="finvista-sidebar-inner">
          <button
            type="button"
            className="finvista-sidebar-toggle"
            onClick={() =>
              setSidebarRecolhida(
                (valorAtual) => !valorAtual,
              )
            }
            aria-label={
              sidebarRecolhida
                ? 'Expandir menu lateral'
                : 'Recolher menu lateral'
            }
            title={
              sidebarRecolhida
                ? 'Expandir menu'
                : 'Recolher menu'
            }
          >
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                d={
                  sidebarRecolhida
                    ? 'M9 6L15 12L9 18'
                    : 'M15 6L9 12L15 18'
                }
              />
            </svg>
          </button>

          <div className="finvista-brand">
            <div className="finvista-brand-mark">
              <span />
              <span />
              <span />
            </div>

            <div className="finvista-brand-text">
              <strong>FinVista</strong>
              <small>
                Financial Intelligence
              </small>
            </div>
          </div>

          <div className="finvista-sidebar-divider" />

          <nav
            className="finvista-navigation"
            aria-label="Navegação principal"
          >
            <span className="finvista-navigation-label">
              VISÃO GERAL
            </span>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'metas'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#metas"
              onClick={(event) => {
                event.preventDefault()
                abrirMetas()
              }}
            >
              <span className="finvista-nav-icon">
                <GoalsIcon />
              </span>

              <span>Metas</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'dashboard' &&
                secaoAtiva === 'dashboard'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#dashboard"
              onClick={(event) => {
                event.preventDefault()
                abrirDashboard()
              }}
            >
              <span className="finvista-nav-icon">
                <DashboardIcon />
              </span>

              <span>Painel executivo</span>
            </a>

            <span className="finvista-navigation-label finvista-navigation-group">
              FINANCEIRO
            </span>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'dashboard' &&
                secaoAtiva === 'projecao'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#projecao"
              onClick={(event) =>
                navegarParaSecao(
                  event,
                  'projecao',
                )
              }
            >
              <span className="finvista-nav-icon">
                <ProjectionIcon />
              </span>

              <span>Projeção</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'dashboard' &&
                secaoAtiva === 'fluxo-caixa'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#fluxo-caixa"
              onClick={(event) =>
                navegarParaSecao(
                  event,
                  'fluxo-caixa',
                )
              }
            >
              <span className="finvista-nav-icon">
                <CashFlowIcon />
              </span>

              <span>Fluxo de caixa</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'dashboard' &&
                secaoAtiva === 'centros-custo'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#centros-custo"
              onClick={(event) =>
                navegarParaSecao(
                  event,
                  'centros-custo',
                )
              }
            >
              <span className="finvista-nav-icon">
                <CostCenterIcon />
              </span>

              <span>Centros de custo</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'dashboard' &&
                secaoAtiva === 'despesas'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#despesas"
              onClick={(event) =>
                navegarParaSecao(
                  event,
                  'despesas',
                )
              }
            >
              <span className="finvista-nav-icon">
                <ExpenseIcon />
              </span>

              <span>Despesas</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'dashboard' &&
                secaoAtiva === 'orcamentos'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#orcamentos"
              onClick={(event) =>
                navegarParaSecao(
                  event,
                  'orcamentos',
                )
              }
            >
              <span className="finvista-nav-icon">
                <BudgetIcon />
              </span>

              <span>Orçamentos</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === 'dashboard' &&
                secaoAtiva === 'historico'
                  ? 'finvista-nav-item-active'
                  : ''
              }`}
              href="#historico"
              onClick={(event) =>
                navegarParaSecao(
                  event,
                  'historico',
                )
              }
            >
              <span className="finvista-nav-icon">
                <HistoryIcon />
              </span>

              <span>Histórico</span>
            </a>

            {usuarioAdmin && (
              <>
                <span className="finvista-navigation-label finvista-navigation-group">
                  DADOS
                </span>

                <a
                  className={`finvista-nav-item ${
                    paginaAtiva === 'importacao'
                      ? 'finvista-nav-item-active'
                      : ''
                  }`}
                  href="#importacao"
                  onClick={(event) => {
                    event.preventDefault()
                    abrirImportacao()
                  }}
                >
                  <span className="finvista-nav-icon">
                    <ImportIcon />
                  </span>

                  <span>Importar dados</span>
                </a>
              </>
            )}
          </nav>

          <div className="finvista-sidebar-bottom">
            <div className="finvista-system-status">
              <span className="finvista-system-status-dot" />

              <div>
                <strong>
                  Sistema operacional
                </strong>

                <small>
                  Dados atualizados
                </small>
              </div>
            </div>

            <div className="finvista-sidebar-version">
              FinVista
              <span>v1.0</span>
            </div>
          </div>
        </div>
      </aside>

      {menuMobileAberto && (
        <button
          type="button"
          className="finvista-mobile-overlay"
          onClick={fecharMenuMobile}
          aria-label="Fechar menu"
        />
      )}

      <div className="finvista-workspace">
        <header className="finvista-topbar">
          <div className="finvista-topbar-left">
            <button
              type="button"
              className="finvista-mobile-menu-button"
              onClick={() =>
                setMenuMobileAberto(
                  (valorAtual) => !valorAtual,
                )
              }
              aria-label={
                menuMobileAberto
                  ? 'Fechar menu'
                  : 'Abrir menu'
              }
              aria-expanded={menuMobileAberto}
            >
              <span />
              <span />
              <span />
            </button>

            <div className="finvista-topbar-title">
              <span className="finvista-topbar-eyebrow">
                FINVISTA
              </span>

              <h1>
                {paginaAtiva === 'dashboard'
                  ? 'Painel executivo'
                  : paginaAtiva === 'metas'
                    ? 'Metas financeiras'
                    : 'Importação de dados'}
              </h1>
            </div>
          </div>

          <div className="finvista-topbar-actions">
            <div className="finvista-topbar-user">
              <div className="finvista-topbar-user-avatar">
                {usuario.nome
                  .trim()
                  .charAt(0)
                  .toUpperCase()}
              </div>

              <div className="finvista-topbar-user-info">
                <small>
                  {usuario.perfil === 'ADMIN'
                    ? 'ADMINISTRADOR'
                    : 'CLIENTE'}
                </small>

                <strong>
                  {usuario.nome}
                </strong>
              </div>

              <button
                type="button"
                className="finvista-topbar-logout"
                onClick={() => {
                  void realizarLogout()
                }}
                title="Sair do FinVista"
              >
                <LogoutIcon />
                <span>Sair</span>
              </button>
            </div>

            <div className="finvista-topbar-status">
              <span className="finvista-topbar-status-dot" />

              <div>
                <small>AMBIENTE</small>
                <strong>Dados locais</strong>
              </div>
            </div>

            <div className="finvista-topbar-period">
              <small>REFERÊNCIA</small>
              <strong>Setembro 2026</strong>
            </div>

            <button
              type="button"
              className="finvista-topbar-refresh"
              title="Atualização automática será integrada posteriormente"
              aria-label="Atualizar dados"
              disabled
            >
              <svg
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path d="M20 6V10H16" />
                <path d="M4 18V14H8" />
                <path d="M18.5 9A7 7 0 0 0 6.3 6.3L4 10" />
                <path d="M5.5 15A7 7 0 0 0 17.7 17.7L20 14" />
              </svg>

              <span>Atualizar</span>
            </button>
          </div>
        </header>

        <main className="finvista-main">
          {paginaAtiva === 'dashboard' ? (
            <Dashboard />
          ) : paginaAtiva === 'metas' ? (
            <SpendingGoals />
          ) : usuarioAdmin ? (
            <ImportData />
          ) : (
            <SpendingGoals />
          )}
        </main>

        <footer className="finvista-footer">
          <div className="finvista-footer-brand">
            <div className="finvista-footer-mark">
              <span />
              <span />
              <span />
            </div>

            <div>
              <strong>FinVista</strong>
              <small>
                Inteligência financeira
              </small>
            </div>
          </div>

          <div className="finvista-footer-info">
            <div className="finvista-footer-item">
              <small>DADOS</small>

              <span>
                <i className="finvista-footer-status-dot" />
                Ambiente local
              </span>
            </div>

            <div className="finvista-footer-separator" />

            <div className="finvista-footer-item">
              <small>VERSÃO</small>
              <span>v1.0</span>
            </div>

            <div className="finvista-footer-separator" />

            <span className="finvista-footer-copyright">
              © 2026 FinVista
            </span>
          </div>
        </footer>
      </div>
    </div>
  )
}

export default App