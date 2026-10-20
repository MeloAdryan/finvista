import logoEscuro from "../assets/logo-skyline-escuro.svg";
import "../styles/brand-assets.css";
import { type FormEvent, useState } from "react";

import { login, type AuthUser } from "../services/authService";

import "../styles/login.css";

interface LoginProps {
  onLogin: (usuario: AuthUser) => void;
}

function Login({ onLogin }: LoginProps) {
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [mostrarSenha, setMostrarSenha] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [ajuda, setAjuda] = useState(false);

  const [carregando, setCarregando] = useState(false);

  const [erro, setErro] = useState<string | null>(null);

  const enviarLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!email.trim() || !senha) {
      setErro("Informe seu e-mail e sua senha.");
      return;
    }

    try {
      setCarregando(true);
      setErro(null);

      const usuario = await login(email.trim(), senha);

      onLogin(usuario);
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Não foi possível entrar no FinVista.",
      );
    } finally {
      setCarregando(false);
    }
  };

  return (
    <main className="finvista-login">
      <section className="finvista-login-brand">
        <div className="finvista-login-brand-content">
<div className="finvista-login-logo"><img className="finvista-brand-image" src={logoEscuro} alt="FinVista — Financial Intelligence" /></div>

          <div className="finvista-login-message">
            <span className="finvista-login-eyebrow">GESTÃO FINANCEIRA</span>

            <h1>Transforme números em decisões e metas em resultados.</h1>

            <p>
              Acompanhe metas financeiras, projeções, fluxo de caixa e
              indicadores em um único ambiente.
            </p>
          </div>

          <div className="finvista-login-examples" aria-label="Exemplos ilustrativos">
            <div className="finvista-login-example-grid">
              <div className="finvista-login-example">
                <span>Resultado do mês</span><strong>R$ 48.220</strong>
                <small className="finvista-login-positive">▲ 8,4% vs. mês anterior</small>
                <svg viewBox="0 0 300 48" aria-hidden="true"><defs><linearGradient id="login-chart-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#e88a20" stopOpacity=".3"/><stop offset="100%" stopColor="#e88a20" stopOpacity="0"/></linearGradient></defs><path d="M0 38L38 32L76 35L114 23L152 26L190 15L228 18L266 7L300 10V48H0Z" fill="url(#login-chart-fill)"/><path d="M0 38L38 32L76 35L114 23L152 26L190 15L228 18L266 7L300 10" fill="none" stroke="#e88a20" strokeWidth="3"/></svg>
              </div>
              <div className="finvista-login-example">
                <span>Meta de gastos</span><strong>74%</strong>
                <small className="finvista-login-warning">▲ Atenção</small>
                <div className="finvista-login-meter"><i /></div>
                <span className="finvista-login-example-note">Alerta configurado em 70%</span>
              </div>
            </div>
            <p>Dados de exemplo, apenas ilustrativos.</p>
          </div>
          <div className="finvista-login-features">
            <div>
              <span aria-hidden="true">◎</span>

              <p>
                <strong>Metas</strong>
                <small>
                  Acompanhe limites, progresso e situação financeira.
                </small>
              </p>
            </div>

            <div>
              <span aria-hidden="true">↗</span>

              <p>
                <strong>Projeções</strong>
                <small>Antecipe cenários e acompanhe os próximos meses.</small>
              </p>
            </div>

            <div>
              <span aria-hidden="true">♧</span>

              <p>
                <strong>Inteligência</strong>
                <small>
                  Dados organizados para apoiar decisões financeiras.
                </small>
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="finvista-login-access">
        <div className="finvista-login-card">
          <div className="finvista-login-card-header">

            <h2>Bem-vindo ao FinVista</h2>

            <p>
              Entre com suas credenciais para acessar o ambiente financeiro.
            </p>
          </div>

          <form className="finvista-login-form" onSubmit={enviarLogin} aria-busy={carregando}>
            {erro && <div className="finvista-login-error" role="alert"><span aria-hidden="true">!</span><p>{/inválid|incorret/i.test(erro) ? <><strong>E-mail ou senha incorretos.</strong> Confira os dados e tente novamente.</> : erro}</p></div>}
            <label>
              <span>E-mail</span>

              <div className="finvista-login-input">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4 6H20V18H4V6Z" />
                  <path d="M4 7L12 13L20 7" />
                </svg>

                <input
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="voce@empresa.com.br"
                  required
                  aria-invalid={Boolean(erro)}
                  autoComplete="email"
                  disabled={carregando}
                />
              </div>
            </label>

            <div className="finvista-login-password-field">
              <div className="finvista-login-label-row"><label htmlFor="finvista-login-password">Senha</label><button type="button" className="finvista-login-help" onClick={() => setAjuda(v => !v)}>Esqueceu a senha?</button></div>

              <div className="finvista-login-input">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="5" y="10" width="14" height="10" rx="2" />
                  <path d="M8 10V7A4 4 0 0 1 16 7V10" />
                </svg>

                <input
                  id="finvista-login-password"
                  type={mostrarSenha ? "text" : "password"}
                  value={senha}
                  onChange={(event) => setSenha(event.target.value)}
                  placeholder="Digite sua senha"
                  required
                  aria-invalid={Boolean(erro)}
                  onKeyDown={(event) => setCapsLock(event.getModifierState("CapsLock"))}
                  onKeyUp={(event) => setCapsLock(event.getModifierState("CapsLock"))}
                  onBlur={() => setCapsLock(false)}
                  autoComplete="current-password"
                  disabled={carregando}
                />

                <button
                  type="button"
                  className="finvista-login-password-toggle"
                  onClick={() => setMostrarSenha((valor) => !valor)}
                  disabled={carregando}
                  aria-label={mostrarSenha ? "Ocultar senha" : "Mostrar senha"}
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>{mostrarSenha && <path d="M3 3L21 21"/>}</svg>
                </button>
              </div>
            </div>

            {capsLock && <p className="finvista-login-caps" role="status">⇪ Caps Lock está ativado</p>}
            {ajuda && <p className="finvista-login-help-message" role="status">Para recuperar seu acesso, fale com o administrador da sua empresa.</p>}

            <button
              type="submit"
              className="finvista-login-submit"
              disabled={carregando}
            >
              <span>{carregando ? "Entrando..." : "Entrar no FinVista"}</span>

              {carregando ? <span className="finvista-login-spinner" aria-hidden="true" /> : (
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M5 12H19" />
                  <path d="M14 7L19 12L14 17" />
                </svg>
              )}
            </button>
          </form>

          <div className="finvista-login-security">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3L20 6V12c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="M8 12l3 3 5-6"/></svg>
            <p>Acesso restrito a usuários autorizados</p>
          </div>
          <p className="finvista-login-contact">Não tem acesso? <button type="button" onClick={() => setAjuda(true)}>Fale com o administrador da sua empresa</button></p>
        </div>

        <footer className="finvista-login-footer">
          <span>© 2026 FinVista</span>
        </footer>
      </section>
    </main>
  );
}

export default Login;
