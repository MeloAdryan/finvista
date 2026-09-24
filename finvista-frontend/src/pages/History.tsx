import { useEffect, useState } from "react";

import { getHistory, type HistoryData } from "../services/historyService";

import HistoryChart from "../charts/HistoryChart";

import "../styles/dashboard.css";

function History() {
  const [periodoInicial, setPeriodoInicial] = useState("");
  const [periodoFinal, setPeriodoFinal] = useState("");

  const [historico, setHistorico] = useState<HistoryData[]>([]);

  const [carregando, setCarregando] = useState(true);
  const [carregandoHistorico, setCarregandoHistorico] = useState(false);

  const [erro, setErro] = useState<string | null>(null);
  const [erroHistorico, setErroHistorico] = useState<string | null>(null);

  useEffect(() => {
    async function carregarHistorico() {
      try {
        const dadosHistorico = await getHistory();

        setHistorico(dadosHistorico);
      } catch (error) {
        console.error("Erro ao carregar histórico:", error);

        if (error instanceof Error) {
          setErro(error.message);
        } else {
          setErro("Não foi possível carregar o histórico financeiro.");
        }
      } finally {
        setCarregando(false);
      }
    }

    carregarHistorico();
  }, []);

  async function aplicarFiltrosHistorico() {
    if (periodoInicial && periodoFinal && periodoInicial > periodoFinal) {
      setErroHistorico(
        "O período inicial não pode ser maior que o período final.",
      );

      return;
    }

    try {
      setCarregandoHistorico(true);
      setErroHistorico(null);

      const dadosFiltrados = await getHistory(
        periodoInicial || undefined,
        periodoFinal || undefined,
      );

      setHistorico(dadosFiltrados);
    } catch (error) {
      console.error("Erro ao filtrar histórico:", error);

      setErroHistorico("Não foi possível aplicar os filtros.");
    } finally {
      setCarregandoHistorico(false);
    }
  }

  if (carregando) {
    return <p>Carregando histórico...</p>;
  }

  if (erro) {
    return <p>{erro}</p>;
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>Histórico financeiro</h1>
          <p>Consulte a evolução financeira por período</p>
        </div>
      </header>

      <section id="historico" className="history-section">
        <div className="history-filters">
          <div className="filter-group">
            <label htmlFor="periodo-inicial">Período inicial</label>

            <input
              id="periodo-inicial"
              type="month"
              value={periodoInicial}
              onChange={(event) => setPeriodoInicial(event.target.value)}
            />
          </div>

          <div className="filter-group">
            <label htmlFor="periodo-final">Período final</label>

            <input
              id="periodo-final"
              type="month"
              value={periodoFinal}
              onChange={(event) => setPeriodoFinal(event.target.value)}
            />
          </div>

          <button
            type="button"
            className="filter-button"
            onClick={aplicarFiltrosHistorico}
            disabled={carregandoHistorico}
          >
            {carregandoHistorico ? "Filtrando..." : "Aplicar filtros"}
          </button>
        </div>

        {erroHistorico && <p className="history-error">{erroHistorico}</p>}

        <HistoryChart dados={historico} />
      </section>
    </div>
  );
}

export default History;