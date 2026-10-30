import VisualThemeToggle from "./components/VisualThemeToggle";
import FinancialCriteriaWorkspace from "./components/FinancialCriteriaWorkspace";
import marcaIcone from "./assets/brand-icon.svg";
import logoEscuro from "./assets/logo-skyline-escuro.svg";
import "./styles/brand-assets.css";
import { lazy, useCallback, useEffect, useRef, useState } from "react";

import { useLocation, useNavigate } from "react-router-dom";

import AnalysisPeriodProvider from "./contexts/AnalysisPeriodProvider";

import Login from "./pages/Login";
import LoadingScreen from "./components/splash/LoadingScreen";
import WelcomeScreen from "./components/splash/WelcomeScreen";

import ClientSelector from "./components/ClientSelector";

import {
  obterClienteAtual,
  selecionarCliente,
  type Cliente,
} from "./services/clientService";

import {
  logout,
  obterUsuarioAtual,
  type AuthUser,
} from "./services/authService";

import {
  obterReferenciaFinanceira,
  type FinancialReference,
} from "./services/financialReferenceService";

import "./styles/app-layout.css";
import "./styles/shell-navigation.css";
import { useSidebarDrawer } from "./hooks/useSidebarDrawer";

import PageBoundary from "./components/PageBoundary";

const Dashboard = lazy(() => import("./pages/Dashboard"));
const AlertCenter = lazy(() => import("./pages/AlertCenter"));
const SpendingGoals = lazy(() => import("./pages/SpendingGoals"));
const ImportData = lazy(() => import("./pages/ImportData"));
const Projection = lazy(() => import("./pages/Projection"));
const CashFlow = lazy(() => import("./pages/CashFlow"));
const CostCenters = lazy(() => import("./pages/CostCenters"));
const Expenses = lazy(() => import("./pages/Expenses"));
const Budgets = lazy(() => import("./pages/Budgets"));
const FinancialControlCenter = lazy(
  () => import("./pages/FinancialControlCenter"),
);
const History = lazy(() => import("./pages/History"));
const AdminRegistration = lazy(() => import("./components/AdminRegistration"));

type PaginaAtiva =
  | "dashboard"
  | "alertas"
  | "centros-custo"
  | "metas"
  | "projecao"
  | "despesas"
  | "orcamentos"
  | "fluxo-caixa"
  | "conferencia"
  | "historico"
  | "importacao"
  | "cadastros";

function DashboardIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="2" />

      <rect x="14" y="3" width="7" height="7" rx="2" />

      <rect x="3" y="14" width="7" height="7" rx="2" />

      <rect x="14" y="14" width="7" height="7" rx="2" />
    </svg>
  );
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
  );
}

function ProjectionIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 18V6" />

      <path d="M4 18H20" />

      <path d="M7 14L11 10L14 13L20 7" />

      <path d="M16 7H20V11" />
    </svg>
  );
}

function CashFlowIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 7H18" />

      <path d="M15 4L18 7L15 10" />

      <path d="M17 17H6" />

      <path d="M9 14L6 17L9 20" />
    </svg>
  );
}

function CostCenterIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />

      <circle cx="12" cy="12" r="4" />

      <circle cx="12" cy="12" r="1" />
    </svg>
  );
}

function ExpenseIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 19V11" />

      <path d="M10 19V5" />

      <path d="M16 19V8" />

      <path d="M22 19H2" />
    </svg>
  );
}

function BudgetIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 3H19V21L16 19L13 21L10 19L7 21L5 19V3Z" />

      <path d="M9 8H15" />

      <path d="M9 12H15" />

      <path d="M9 16H13" />
    </svg>
  );
}

function HistoryIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 12A9 9 0 1 0 6 5.3" />

      <path d="M3 4V10H9" />

      <path d="M12 7V12L15 14" />
    </svg>
  );
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
  );
}

function ClientsIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="9" cy="7" r="3" />
      <path d="M3 21v-3a6 6 0 0 1 12 0v3" />
      <path d="M16 4a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 4v3" />
    </svg>
  );
}

function LogoutIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 5H5V19H10" />

      <path d="M14 8L18 12L14 16" />

      <path d="M18 12H9" />
    </svg>
  );
}

function App() {
  const location = useLocation();

  const navigate = useNavigate();

  const [usuario, setUsuario] = useState<AuthUser | null>(null);

  const [verificandoSessao, setVerificandoSessao] = useState(true);
  const [mostrarBoasVindas, setMostrarBoasVindas] = useState(false);
  const focarPainel = useRef(false);
  const continuarBoasVindas = useCallback(() => {
    focarPainel.current = true;
    setMostrarBoasVindas(false);
  }, []);

  const [clienteAtual, setClienteAtual] = useState<Cliente | null>(null);

  const [verificandoCliente, setVerificandoCliente] = useState(false);

  const [referenciaFinanceira, setReferenciaFinanceira] =
    useState<FinancialReference | null>(null);

  const paginaAtiva: PaginaAtiva =
    location.pathname === "/admin/cadastros" && usuario?.perfil === "ADMIN"
      ? "cadastros"
      : location.pathname === "/dashboard" || location.pathname === "/"
        ? "dashboard"
        : location.pathname === "/alertas"
          ? "alertas"
          : location.pathname === "/projecao"
            ? "projecao"
            : location.pathname === "/fluxo-caixa"
              ? "fluxo-caixa"
              : location.pathname === "/centros-custo"
                ? "centros-custo"
                : location.pathname === "/despesas"
                  ? "despesas"
                  : location.pathname === "/orcamentos"
                    ? "orcamentos"
                    : location.pathname === "/conferencia-financeira"
                      ? "conferencia"
                      : location.pathname === "/historico"
                        ? "historico"
                        : location.pathname === "/importar"
                          ? "importacao"
                          : "metas";

  const [sidebarRecolhida, setSidebarRecolhida] = useState(false);

  const [menuMobileAberto, setMenuMobileAberto] = useState(false);
  const { sidebarRef, mobile, drawerOpen } = useSidebarDrawer(
    menuMobileAberto && Boolean(usuario),
    setMenuMobileAberto,
  );

  useEffect(() => {
    let ativo = true;

    const verificarSessao = async () => {
      try {
        const usuarioAtual = await obterUsuarioAtual();

        if (ativo) {
          setVerificandoCliente(usuarioAtual?.perfil === "ADMIN");
          setUsuario(usuarioAtual);
        }
      } catch (error) {
        console.error("Erro ao verificar sessão:", error);

        if (ativo) {
          setUsuario(null);
        }
      } finally {
        if (ativo) {
          setVerificandoSessao(false);
        }
      }
    };

    void verificarSessao();

    return () => {
      ativo = false;
    };
  }, []);

  useEffect(() => {
    let ativo = true;

    const verificarCliente = async () => {
      if (!usuario) {
        if (ativo) {
          setClienteAtual(null);
          setVerificandoCliente(false);
        }

        return;
      }

      /*
       * Usuários comuns não precisam escolher cliente.
       * O backend resolve automaticamente o cliente
       * associado ao usuário.
       */
      if (usuario.perfil !== "ADMIN") {
        if (ativo) {
          setClienteAtual(null);
          setVerificandoCliente(false);
        }

        return;
      }

      try {
        setVerificandoCliente(true);

        const cliente = await obterClienteAtual();

        if (ativo) {
          setClienteAtual(cliente);
        }
      } catch (error) {
        console.error("Erro ao verificar cliente atual:", error);

        if (ativo) {
          setClienteAtual(null);
        }
      } finally {
        if (ativo) {
          setVerificandoCliente(false);
        }
      }
    };

    void verificarCliente();

    return () => {
      ativo = false;
    };
  }, [usuario]);

  useEffect(() => {
    let ativo = true;

    const carregarReferenciaFinanceira = async () => {
      if (!usuario) {
        return;
      }

      /*
       * Para ADMIN, a referência só pode ser carregada
       * depois que houver um cliente selecionado.
       *
       * Para usuários comuns, o backend identifica
       * automaticamente o cliente da sessão.
       */
      if (usuario.perfil === "ADMIN" && !clienteAtual) {
        return;
      }

      try {
        const referencia = await obterReferenciaFinanceira();

        if (ativo) {
          setReferenciaFinanceira(referencia);
        }
      } catch (error) {
        console.error("Erro ao carregar referência financeira:", error);

        if (ativo) {
          setReferenciaFinanceira(null);
        }
      }
    };

    void carregarReferenciaFinanceira();

    return () => {
      ativo = false;
    };
  }, [usuario, clienteAtual]);

  const selecionarClienteAdmin = async (cliente: Cliente) => {
    const clienteSelecionado = await selecionarCliente(cliente.id);

    setClienteAtual(clienteSelecionado);
  };

  const fecharMenuMobile = () => {
    setMenuMobileAberto(false);
  };

  const abrirMetas = () => {
    fecharMenuMobile();

    navigate("/metas");

    window.scrollTo({
      top: 0,

      behavior: "smooth",
    });
  };

  const abrirDashboard = () => {
    fecharMenuMobile();

    navigate("/dashboard");

    window.scrollTo({
      top: 0,

      behavior: "smooth",
    });
  };

  const abrirImportacao = () => {
    if (usuario?.perfil !== "ADMIN") {
      abrirMetas();

      return;
    }

    navigate("/importar");

    fecharMenuMobile();

    window.scrollTo({
      top: 0,

      behavior: "smooth",
    });
  };

  const realizarLogout = async () => {
    try {
      await logout();
    } catch (error) {
      console.error("Erro ao encerrar sessão:", error);
    } finally {
      setClienteAtual(null);

      setMostrarBoasVindas(false);
      setUsuario(null);

      navigate("/metas");

      fecharMenuMobile();
    }
  };

  if (verificandoSessao) {
    return <LoadingScreen key="sessao" />;
  }

  if (!usuario) {
    return (
      <Login
        onLogin={(usuarioAutenticado) => {
          setVerificandoCliente(usuarioAutenticado.perfil === "ADMIN");
          setMostrarBoasVindas(true);
          setUsuario(usuarioAutenticado);

          navigate("/dashboard");
        }}
      />
    );
  }
  if (usuario.perfil === "ADMIN" && verificandoCliente) {
    return (
      <LoadingScreen
        key="cliente"
        messages={[
          "Preparando seu acesso…",
          "Carregando a empresa selecionada…",
          "A consulta está demorando um pouco mais. Aguarde…",
        ]}
      />
    );
  }

  if (
    usuario.perfil === "ADMIN" &&
    !clienteAtual &&
    paginaAtiva !== "cadastros"
  ) {
    return <ClientSelector onSelecionar={selecionarClienteAdmin} />;
  }
  if (mostrarBoasVindas) {
    return (
      <WelcomeScreen nome={usuario.nome} onContinue={continuarBoasVindas} />
    );
  }
  const usuarioAdmin = usuario.perfil === "ADMIN";

  return (
    <div
      className={`finvista-app finvista-shell-v2 ${
        sidebarRecolhida ? "finvista-app-sidebar-collapsed" : ""
      }`}
    >
      <aside
        id="finvista-sidebar"
        ref={sidebarRef}
        role={drawerOpen ? "dialog" : undefined}
        aria-modal={drawerOpen ? true : undefined}
        aria-label={drawerOpen ? "Menu principal" : "Menu lateral"}
        inert={mobile && !drawerOpen}
        className={`finvista-sidebar ${
          menuMobileAberto ? "finvista-sidebar-mobile-open" : ""
        }`}
      >
        <div className="finvista-sidebar-inner">
          <button
            type="button"
            className="finvista-sidebar-toggle"
            onClick={() => setSidebarRecolhida((valorAtual) => !valorAtual)}
            aria-label={
              sidebarRecolhida
                ? "Expandir menu lateral"
                : "Recolher menu lateral"
            }
            title={sidebarRecolhida ? "Expandir menu" : "Recolher menu"}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d={sidebarRecolhida ? "M9 6L15 12L9 18" : "M15 6L9 12L15 18"}
              />
            </svg>
          </button>

          <button
            type="button"
            className="finvista-drawer-close"
            aria-label="Fechar menu lateral"
            onClick={fecharMenuMobile}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
          <div className="finvista-brand">
            <img
              className="finvista-brand-image"
              src={logoEscuro}
              alt="FinVista — Financial Intelligence"
            />
            <img
              className="finvista-brand-icon"
              src={marcaIcone}
              alt="FinVista"
            />
          </div>

          <div className="finvista-sidebar-divider" />

          <nav className="finvista-navigation" aria-label="Navegação principal">
            <span className="finvista-navigation-label">VISÃO GERAL</span>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "dashboard" ? "finvista-nav-item-active" : ""
              }`}
              href="/dashboard"
              aria-label="Painel executivo"
              title="Painel executivo"
              aria-current={paginaAtiva === "dashboard" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();

                abrirDashboard();
              }}
            >
              <span className="finvista-nav-icon">
                <DashboardIcon />
              </span>

              <span>Painel executivo</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "metas" ? "finvista-nav-item-active" : ""
              }`}
              href="/metas"
              aria-label="Metas"
              title="Metas"
              aria-current={paginaAtiva === "metas" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();

                abrirMetas();
              }}
            >
              <span className="finvista-nav-icon">
                <GoalsIcon />
              </span>

              <span>Metas</span>
            </a>

            <a
              className={`finvista-nav-item ${paginaAtiva === "alertas" ? "finvista-nav-item-active" : ""}`}
              href="/alertas"
              aria-label="Central de alertas"
              title="Central de alertas"
              aria-current={paginaAtiva === "alertas" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();
                setMenuMobileAberto(false);
                navigate("/alertas");
              }}
            >
              <span className="finvista-nav-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 3a6 6 0 0 0-6 6v5l-2 3h16l-2-3V9a6 6 0 0 0-6-6Z" />
                  <path d="M9 20h6" />
                </svg>
              </span>
              <span>Central de alertas</span>
            </a>
            <span className="finvista-navigation-label finvista-navigation-group">
              FINANCEIRO
            </span>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "projecao" ? "finvista-nav-item-active" : ""
              }`}
              href="/projecao"
              aria-label="Projeção"
              title="Projeção"
              aria-current={paginaAtiva === "projecao" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();

                fecharMenuMobile();

                navigate("/projecao");

                window.scrollTo({
                  top: 0,

                  behavior: "smooth",
                });
              }}
            >
              <span className="finvista-nav-icon">
                <ProjectionIcon />
              </span>

              <span>Projeção</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "fluxo-caixa" ? "finvista-nav-item-active" : ""
              }`}
              href="/fluxo-caixa"
              aria-label="Fluxo de caixa"
              title="Fluxo de caixa"
              aria-current={paginaAtiva === "fluxo-caixa" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();

                fecharMenuMobile();

                navigate("/fluxo-caixa");

                window.scrollTo({
                  top: 0,

                  behavior: "smooth",
                });
              }}
            >
              <span className="finvista-nav-icon">
                <CashFlowIcon />
              </span>

              <span>Fluxo de caixa</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "centros-custo"
                  ? "finvista-nav-item-active"
                  : ""
              }`}
              href="/centros-custo"
              aria-label="Centros de custo"
              title="Centros de custo"
              aria-current={
                paginaAtiva === "centros-custo" ? "page" : undefined
              }
              onClick={(event) => {
                event.preventDefault();

                fecharMenuMobile();

                navigate("/centros-custo");

                window.scrollTo({
                  top: 0,

                  behavior: "smooth",
                });
              }}
            >
              <span className="finvista-nav-icon">
                <CostCenterIcon />
              </span>

              <span>Centros de custo</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "despesas" ? "finvista-nav-item-active" : ""
              }`}
              href="/despesas"
              aria-label="Despesas"
              title="Despesas"
              aria-current={paginaAtiva === "despesas" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();

                fecharMenuMobile();

                navigate("/despesas");

                window.scrollTo({
                  top: 0,

                  behavior: "smooth",
                });
              }}
            >
              <span className="finvista-nav-icon">
                <ExpenseIcon />
              </span>

              <span>Despesas</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "orcamentos" ? "finvista-nav-item-active" : ""
              }`}
              href="/orcamentos"
              aria-label="Orçamentos"
              title="Orçamentos"
              aria-current={paginaAtiva === "orcamentos" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();

                fecharMenuMobile();

                navigate("/orcamentos");

                window.scrollTo({
                  top: 0,

                  behavior: "smooth",
                });
              }}
            >
              <span className="finvista-nav-icon">
                <BudgetIcon />
              </span>

              <span>Orçamentos</span>
            </a>

            <a
              className={`finvista-nav-item ${
                paginaAtiva === "historico" ? "finvista-nav-item-active" : ""
              }`}
              href="/historico"
              aria-label="Histórico"
              title="Histórico"
              aria-current={paginaAtiva === "historico" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();

                fecharMenuMobile();

                navigate("/historico");

                window.scrollTo({
                  top: 0,

                  behavior: "smooth",
                });
              }}
            >
              <span className="finvista-nav-icon">
                <HistoryIcon />
              </span>

              <span>Histórico</span>
            </a>

            <a
              className={`finvista-nav-item ${paginaAtiva === "conferencia" ? "finvista-nav-item-active" : ""}`}
              href="/conferencia-financeira"
              aria-label="Conferência financeira"
              title="Conferência financeira"
              aria-current={paginaAtiva === "conferencia" ? "page" : undefined}
              onClick={(event) => {
                event.preventDefault();
                fecharMenuMobile();
                navigate("/conferencia-financeira");
                window.scrollTo({ top: 0 });
              }}
            >
              <span className="finvista-nav-icon">
                <HistoryIcon />
              </span>
              <span>Conferência financeira</span>
            </a>
            {usuarioAdmin && (
              <>
                <span className="finvista-navigation-label finvista-navigation-group">
                  DADOS
                </span>

                <a
                  className={`finvista-nav-item ${paginaAtiva === "conferencia" ? "Conferência financeira" : paginaAtiva === "cadastros" ? "finvista-nav-item-active" : ""}`}
                  href="/admin/cadastros"
                  aria-label="Clientes e usuários"
                  title="Clientes e usuários"
                  aria-current={
                    paginaAtiva === "cadastros" ? "page" : undefined
                  }
                  onClick={(event) => {
                    event.preventDefault();
                    fecharMenuMobile();
                    navigate("/admin/cadastros");
                    window.scrollTo({ top: 0 });
                  }}
                >
                  <span className="finvista-nav-icon">
                    <ClientsIcon />
                  </span>
                  <span>Clientes e usuários</span>
                </a>

                <a
                  className={`finvista-nav-item ${
                    paginaAtiva === "importacao"
                      ? "finvista-nav-item-active"
                      : ""
                  }`}
                  href="/importar"
                  aria-label="Importar dados"
                  title="Importar dados"
                  aria-current={
                    paginaAtiva === "importacao" ? "page" : undefined
                  }
                  onClick={(event) => {
                    event.preventDefault();

                    abrirImportacao();
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
            <div
              className="finvista-sidebar-user"
              role="group"
              aria-label={`Usuário: ${usuario.nome}`}
              title={usuario.nome}
            >
              <span className="finvista-user-avatar" aria-hidden="true">
                {usuario.nome.trim().charAt(0).toUpperCase()}
              </span>
              <div className="finvista-user-info">
                <strong title={usuario.nome}>{usuario.nome}</strong>
                <span>{usuarioAdmin ? "Administrador" : "Cliente"}</span>
              </div>
            </div>
            <button
              type="button"
              className="finvista-sidebar-logout"
              onClick={() => {
                void realizarLogout();
              }}
              title="Sair do FinVista"
              aria-label="Sair do FinVista"
            >
              <LogoutIcon />
              <span>Sair</span>
            </button>
          </div>
        </div>
      </aside>

      {drawerOpen && (
        <button
          type="button"
          className="finvista-mobile-overlay"
          onClick={fecharMenuMobile}
          aria-label="Fechar menu"
        />
      )}

      <div className="finvista-workspace" inert={drawerOpen}>
        <header className="finvista-topbar">
          <div className="finvista-topbar-left">
            <button
              type="button"
              className="finvista-mobile-menu-button"
              onClick={() => setMenuMobileAberto((valorAtual) => !valorAtual)}
              aria-label={menuMobileAberto ? "Fechar menu" : "Abrir menu"}
              aria-expanded={drawerOpen}
              aria-controls="finvista-sidebar"
            >
              <span />

              <span />

              <span />
            </button>

            <nav className="finvista-breadcrumb" aria-label="Caminho da página">
              <span>FinVista</span>
              <span aria-hidden="true">›</span>
              <span aria-current="page">
                {paginaAtiva === "cadastros"
                  ? "Clientes e usuários"
                  : paginaAtiva === "dashboard"
                    ? "Painel executivo"
                    : paginaAtiva === "alertas"
                      ? "Central de Alertas"
                      : paginaAtiva === "metas"
                        ? "Metas financeiras"
                        : paginaAtiva === "projecao"
                          ? "Projeção financeira"
                          : paginaAtiva === "fluxo-caixa"
                            ? "Fluxo de caixa"
                            : paginaAtiva === "centros-custo"
                              ? "Centros de custo"
                              : paginaAtiva === "despesas"
                                ? "Despesas"
                                : paginaAtiva === "orcamentos"
                                  ? "Orçamentos"
                                  : paginaAtiva === "historico"
                                    ? "Histórico"
                                    : "Importação de dados"}
              </span>
            </nav>
          </div>

          <div className="finvista-topbar-actions">
            <VisualThemeToggle />
            <span
              className="finvista-data-badge"
              title="Base de dados do FinVista"
            >
              <i aria-hidden="true" />
              Dados do FinVista
            </span>
            <span
              className="finvista-reference-badge"
              hidden={[
                "dashboard",
                "metas",
                "projecao",
                "fluxo-caixa",
                "centros-custo",
                "despesas",
                "orcamentos",
                "historico",
                "conferencia",
              ].includes(paginaAtiva)}
              title="Referência financeira da empresa; os filtros permanecem em cada página"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="5" width="18" height="16" rx="2" />
                <path d="M7 3v4M17 3v4M3 10h18" />
              </svg>
              <span className="finvista-reference-label">Referência:</span>{" "}
              {referenciaFinanceira?.mesAno ?? "Não disponível"}
            </span>
          </div>
        </header>

        <AnalysisPeriodProvider
          key={`${usuario.id}:${clienteAtual?.id ?? "cliente-sessao"}`}
        >
          <main
            className="finvista-main"
            tabIndex={-1}
            ref={(element) => {
              if (element && focarPainel.current) {
                element.focus();
                focarPainel.current = false;
              }
            }}
          >
            <FinancialCriteriaWorkspace
              key={clienteAtual?.id}
              page={paginaAtiva}
              clienteId={clienteAtual?.id}
            >
              <PageBoundary key={paginaAtiva}>
                {paginaAtiva === "cadastros" && usuarioAdmin ? (
                  <AdminRegistration
                    usuarioAtualId={usuario.id}
                    onVoltar={() => navigate("/metas")}
                    textoVoltar="Voltar às metas"
                  />
                ) : paginaAtiva === "dashboard" ? (
                  <Dashboard key={clienteAtual?.id ?? "sem-cliente"} />
                ) : paginaAtiva === "alertas" ? (
                  <AlertCenter />
                ) : paginaAtiva === "metas" ? (
                  <SpendingGoals />
                ) : paginaAtiva === "projecao" ? (
                  <Projection />
                ) : paginaAtiva === "fluxo-caixa" ? (
                  <CashFlow />
                ) : paginaAtiva === "centros-custo" ? (
                  <CostCenters />
                ) : paginaAtiva === "despesas" ? (
                  <Expenses />
                ) : paginaAtiva === "orcamentos" ? (
                  <Budgets />
                ) : paginaAtiva === "conferencia" ? (
                  <FinancialControlCenter
                    key={clienteAtual?.id ?? "sem-cliente"}
                    clienteId={clienteAtual?.id}
                    admin={usuarioAdmin}
                  />
                ) : paginaAtiva === "historico" ? (
                  <History key={clienteAtual?.id ?? "sem-cliente"} />
                ) : usuarioAdmin ? (
                  <ImportData
                    key={clienteAtual?.id ?? "sem-cliente"}
                    cliente={clienteAtual}
                  />
                ) : (
                  <SpendingGoals />
                )}
              </PageBoundary>
            </FinancialCriteriaWorkspace>
          </main>
        </AnalysisPeriodProvider>

        <footer className="finvista-footer">
          <div className="finvista-footer-brand">
            <img
              className="finvista-brand-image"
              src={logoEscuro}
              alt="FinVista — Financial Intelligence"
            />
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

            <span className="finvista-footer-copyright">© 2026 FinVista</span>
          </div>
        </footer>
      </div>
    </div>
  );
}

export default App;
