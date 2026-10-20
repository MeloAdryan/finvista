import { Component, Suspense, type ReactNode } from "react";
import "../styles/page-feedback.css";

export default class PageBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return (
        <section className="finvista-page-feedback" role="alert">
          <h2>Não foi possível abrir esta página</h2>
          <p>Tente outra opção do menu ou recarregue a página.</p>
          <button type="button" onClick={() => window.location.reload()}>
            Recarregar página
          </button>
        </section>
      );
    }
    return (
      <Suspense fallback={
        <p className="finvista-page-feedback" role="status" aria-live="polite">
          Carregando página…
        </p>
      }>
        {this.props.children}
      </Suspense>
    );
  }
}
