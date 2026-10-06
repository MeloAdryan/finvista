import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { obterUsuarioAtual } from "../services/authService";
import {
  getCostAnalysis,
  getCostClassifications,
  saveCostClassification,
  type CostFilters,
  type CostCenterData,
  type CostClassification,
  type CostBehavior,
  type CostNature,
} from "../services/costCenterService";
import "../styles/cost-center-filters.css";

const behaviors: Record<CostBehavior, string> = {
  FIXO: "Fixo",
  VARIAVEL: "Variável",
  NAO_CLASSIFICADO: "Não classificado",
};
const natures: Record<CostNature, string> = {
  TAXAS: "Taxas",
  IMPOSTOS: "Impostos",
  PESSOAL: "Pessoal",
  SERVICOS: "Serviços",
  MATERIAIS: "Materiais",
  OUTROS: "Outros",
  NAO_CLASSIFICADO: "Não classificado",
};
interface Props {
  onResult: (data: CostCenterData[]) => void;
  onLoading: (value: boolean) => void;
  onError: (value: string | null) => void;
}
const showDate = (date: string) => date.split("-").reverse().join("/");

export default function CostCenterFilters({
  onResult,
  onLoading,
  onError,
}: Props) {
  const [draft, setDraft] = useState<CostFilters>({});
  const [active, setActive] = useState<CostFilters>({});
  const [period, setPeriod] = useState("");
  const [loading, setLoading] = useState(false);
  const [admin, setAdmin] = useState(false);
  const [items, setItems] = useState<CostClassification[]>([]);
  const [selection, setSelection] = useState<CostClassification | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true);
      onLoading(true);
      onError(null);
      onResult([]);
      try {
        const data = await getCostAnalysis(active, controller.signal);
        if (controller.signal.aborted) return;
        onResult(data.centros);
        setPeriod(`${showDate(data.inicio)} até ${showDate(data.fim)}`);
        if (!active.inicio && !active.fim) {
          setDraft((current) => ({
            ...current,
            inicio: data.inicio,
            fim: data.fim,
          }));
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          onError(
            error instanceof Error
              ? error.message
              : "Erro ao consultar custos.",
          );
          setPeriod("");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
          onLoading(false);
        }
      }
    }
    void load();
    return () => controller.abort();
  }, [active, revision, onResult, onLoading, onError]);

  useEffect(() => {
    let alive = true;
    obterUsuarioAtual()
      .then((user) => {
        if (alive) setAdmin(user?.perfil === "ADMIN");
      })
      .catch(() => {
        if (alive) setAdmin(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    if (!admin) return;
    const controller = new AbortController();
    getCostClassifications(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setItems(data);
      })
      .catch((error) => {
        if (!controller.signal.aborted) setMessage(error.message);
      });
    return () => controller.abort();
  }, [admin, revision]);

  function apply(event: FormEvent) {
    event.preventDefault();
    setFormError("");
    if (
      Boolean(draft.inicio) !== Boolean(draft.fim) ||
      (draft.inicio && draft.fim && draft.fim < draft.inicio)
    ) {
      setFormError(
        "Informe as duas datas, com a final igual ou posterior à inicial.",
      );
      return;
    }
    setActive({ ...draft });
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (!selection) return;
    setSaving(true);
    setMessage("");
    try {
      await saveCostClassification(selection);
      setRevision((value) => value + 1);
      setMessage(
        "Classificação salva para esta categoria do cliente selecionado.",
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className="cost-filters">
      <h2>Analisar centros de custo</h2>
      <p>
        Combine período, comportamento e natureza. Categorias sem classificação
        continuam nos totais.
      </p>
      <form onSubmit={apply}>
        <div className="cost-filters-grid">
          <label>
            Data inicial
            <input
              type="date"
              value={draft.inicio || ""}
              disabled={loading}
              onChange={(e) => setDraft({ ...draft, inicio: e.target.value })}
            />
          </label>
          <label>
            Data final
            <input
              type="date"
              value={draft.fim || ""}
              disabled={loading}
              onChange={(e) => setDraft({ ...draft, fim: e.target.value })}
            />
          </label>
          <label>
            Comportamento
            <select
              value={draft.comportamento || ""}
              disabled={loading}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  comportamento: e.target.value as CostFilters["comportamento"],
                })
              }
            >
              <option value="">Todos os comportamentos</option>
              {Object.entries(behaviors).map(([key, name]) => (
                <option key={key} value={key}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Natureza
            <select
              value={draft.natureza || ""}
              disabled={loading}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  natureza: e.target.value as CostFilters["natureza"],
                })
              }
            >
              <option value="">Todas as naturezas</option>
              {Object.entries(natures).map(([key, name]) => (
                <option key={key} value={key}>
                  {name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="cost-filter-actions">
          <button type="submit" disabled={loading}>
            {loading ? "Consultando..." : "Aplicar filtros"}
          </button>
          <button
            type="button"
            disabled={loading}
            onClick={() => {
              setDraft({});
              setActive({});
              setFormError("");
            }}
          >
            Limpar e voltar ao mês de referência
          </button>
        </div>
      </form>
      {formError && <p role="alert">{formError}</p>}
      {period && (
        <p aria-live="polite">
          Análise exibida: <strong>{period}</strong>
          {" · "}
          {active.comportamento
            ? behaviors[active.comportamento]
            : "Todos os comportamentos"}
          {" · "}
          {active.natureza ? natures[active.natureza] : "Todas as naturezas"}
        </p>
      )}
      {admin && (
        <details>
          <summary>Classificar categorias deste cliente</summary>
          <p>
            Uma classificação vale para todos os lançamentos da categoria,
            inclusive históricos.
          </p>
          <form onSubmit={save}>
            <div className="cost-filters-grid">
              <label>
                Categoria
                <select
                  value={selection?.categoria || ""}
                  disabled={saving}
                  onChange={(e) => {
                    setSelection(
                      items.find((item) => item.categoria === e.target.value) ||
                        null,
                    );
                    setMessage("");
                  }}
                >
                  <option value="">Selecione uma categoria</option>
                  {items.map((item) => (
                    <option key={item.categoria} value={item.categoria}>
                      {item.categoria}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Comportamento
                <select
                  value={selection?.comportamento || "NAO_CLASSIFICADO"}
                  disabled={!selection || saving}
                  onChange={(e) =>
                    selection &&
                    setSelection({
                      ...selection,
                      comportamento: e.target.value as CostBehavior,
                    })
                  }
                >
                  {Object.entries(behaviors).map(([key, name]) => (
                    <option key={key} value={key}>
                      {name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Natureza
                <select
                  value={selection?.natureza || "NAO_CLASSIFICADO"}
                  disabled={!selection || saving}
                  onChange={(e) =>
                    selection &&
                    setSelection({
                      ...selection,
                      natureza: e.target.value as CostNature,
                    })
                  }
                >
                  {Object.entries(natures).map(([key, name]) => (
                    <option key={key} value={key}>
                      {name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <button disabled={!selection || saving || loading}>
              {saving ? "Salvando..." : "Salvar classificação"}
            </button>
          </form>
          <p role="status">{message}</p>
        </details>
      )}
    </div>
  );
}
