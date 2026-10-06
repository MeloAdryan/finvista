import { Link } from "react-router-dom";
import "../styles/financial-help.css";

export default function DashboardQuickActions() {
  return (
    <section className="dashboard-quick-actions" aria-labelledby="quick-actions-title">
      <h2 id="quick-actions-title">Ações rápidas</h2>
      <div>
        <Link to="/metas">Acompanhar metas</Link>
        <Link to="/centros-custo">Analisar centros de custo</Link>
        <Link to="/despesas">Ver distribuição das despesas</Link>
      </div>
    </section>
  );
}
