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
          <div className="finvista-login-logo">
            <div className="finvista-login-logo-mark">
              <span />
              <span />
              <span />
            </div>

            <div>
              <strong>FinVista</strong>
              <small>Financial Intelligence</small>
            </div>
          </div>

          <div className="finvista-login-message">
            <span className="finvista-login-eyebrow">GESTÃO FINANCEIRA</span>

            <h1>Transforme números em decisões e metas em resultados.</h1>

            <p>
              Acompanhe metas financeiras, projeções, fluxo de caixa e
              indicadores em um único ambiente.
            </p>
          </div>

          <div className="finvista-login-features">
            <div>
              <span>01</span>

              <p>
                <strong>Metas</strong>
                <small>
                  Acompanhe limites, progresso e situação financeira.
                </small>
              </p>
            </div>

            <div>
              <span>02</span>

              <p>
                <strong>Projeções</strong>
                <small>Antecipe cenários e acompanhe os próximos meses.</small>
              </p>
            </div>

            <div>
              <span>03</span>

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
            <span>ACESSO SEGURO</span>

            <h2>Bem-vindo ao FinVista</h2>

            <p>
              Entre com suas credenciais para acessar o ambiente financeiro.
            </p>
          </div>

          <form className="finvista-login-form" onSubmit={enviarLogin}>
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
                  placeholder="seu@email.com"
                  autoComplete="email"
                  disabled={carregando}
                />
              </div>
            </label>

            <label>
              <span>Senha</span>

              <div className="finvista-login-input">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="5" y="10" width="14" height="10" rx="2" />
                  <path d="M8 10V7A4 4 0 0 1 16 7V10" />
                </svg>

                <input
                  type={mostrarSenha ? "text" : "password"}
                  value={senha}
                  onChange={(event) => setSenha(event.target.value)}
                  placeholder="Digite sua senha"
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
                  {mostrarSenha ? "Ocultar" : "Mostrar"}
                </button>
              </div>
            </label>

            {erro && (
              <div className="finvista-login-error" role="alert">
                <span>!</span>
                <p>{erro}</p>
              </div>
            )}

            <button
              type="submit"
              className="finvista-login-submit"
              disabled={carregando}
            >
              <span>{carregando ? "Entrando..." : "Entrar no FinVista"}</span>

              {!carregando && (
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M5 12H19" />
                  <path d="M14 7L19 12L14 17" />
                </svg>
              )}
            </button>
          </form>

          <div className="finvista-login-security">
            <span className="finvista-login-security-dot" />

            <p>
              <strong>Ambiente protegido</strong>

              <small>Acesso restrito a usuários autorizados.</small>
            </p>
          </div>
        </div>

        <footer className="finvista-login-footer">
          <span>FinVista</span>
          <span>© 2026</span>
        </footer>
      </section>
    </main>
  );
}

export default Login;
