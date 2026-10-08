import { useEffect, useState, type FormEvent } from "react";
import { obterUsuarioAtual } from "../services/authService";
import {
  getCostClassifications,
  saveCostClassification,
  type CostClassification,
} from "../services/costCenterService";
export default function CostClassificationEditor({
  onSaved,
}: {
  onSaved: () => void;
}) {
  const [admin, setAdmin] = useState(false);
  const [itens, setItens] = useState<CostClassification[]>([]);
  const [form, setForm] = useState<CostClassification | null>(null);
  const [mensagem, setMensagem] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    let vivo = true;
    const c = new AbortController();
    obterUsuarioAtual()
      .then(async (u) => {
        if (!vivo || u?.perfil !== "ADMIN") return;
        setAdmin(true);
        const lista = await getCostClassifications(c.signal);
        if (vivo) setItens(lista);
      })
      .catch((e) => {
        if (vivo)
          setMensagem(
            e instanceof Error
              ? e.message
              : "Falha ao carregar classificações.",
          );
      });
    return () => {
      vivo = false;
      c.abort();
    };
  }, []);
  async function salvar(e: FormEvent) {
    e.preventDefault();
    if (!form) return;
    setSaving(true);
    try {
      await saveCostClassification(form);
      setItens((atuais) =>
        atuais.map((m) => (m.categoria === form.categoria ? form : m)),
      );
      setMensagem("Classificação salva.");
      onSaved();
    } catch (e) {
      setMensagem(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally {
      setSaving(false);
    }
  }
  if (!admin) return null;
  return (
    <details className="ax-panel">
      <summary>Classificar categorias deste cliente</summary>
      <form onSubmit={(e) => void salvar(e)} className="ax-filter-grid">
        <label>
          Categoria
          <select
            disabled={saving}
            value={form?.categoria ?? ""}
            onChange={(e) =>
              setForm(itens.find((m) => m.categoria === e.target.value) ?? null)
            }
          >
            <option value="">Selecione</option>
            {itens.map((m) => (
              <option key={m.categoria}>{m.categoria}</option>
            ))}
          </select>
        </label>
        {form && (
          <>
            <label>
              Comportamento
              <select
                disabled={saving}
                value={form.comportamento}
                onChange={(e) =>
                  setForm({
                    ...form,
                    comportamento: e.target
                      .value as CostClassification["comportamento"],
                  })
                }
              >
                {[
                  ["FIXO", "Fixo"],
                  ["VARIAVEL", "Variável"],
                  ["NAO_CLASSIFICADO", "Não classificado"],
                ].map(([v, n]) => (
                  <option key={v} value={v}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Natureza
              <select
                disabled={saving}
                value={form.natureza}
                onChange={(e) =>
                  setForm({
                    ...form,
                    natureza: e.target.value as CostClassification["natureza"],
                  })
                }
              >
                {[
                  ["TAXAS", "Taxas"],
                  ["IMPOSTOS", "Impostos"],
                  ["PESSOAL", "Pessoal"],
                  ["SERVICOS", "Serviços"],
                  ["MATERIAIS", "Materiais"],
                  ["OUTROS", "Outros"],
                  ["NAO_CLASSIFICADO", "Não classificado"],
                ].map(([v, n]) => (
                  <option key={v} value={v}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit" disabled={saving}>
              {saving ? "Salvando…" : "Salvar classificação"}
            </button>
          </>
        )}
      </form>
      <p role="status">{mensagem}</p>
    </details>
  );
}
