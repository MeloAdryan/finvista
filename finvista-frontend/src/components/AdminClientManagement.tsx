import { useState, type FormEvent } from "react";
import type { Cliente } from "../services/clientService";
import { atualizarEmpresaAdmin } from "../services/adminRegistrationService";

interface Props {
  empresas: Cliente[];
  ocupado: boolean;
  aoOcupar: (valor: boolean) => void;
  aoAtualizar: (empresa: Cliente) => void;
  aoRecarregar: () => void;
}

export default function AdminClientManagement({
  empresas,
  ocupado,
  aoOcupar,
  aoAtualizar,
  aoRecarregar,
}: Props) {
  const [original, setOriginal] = useState<Cliente | null>(null);
  const [nome, setNome] = useState("");
  const [ativo, setAtivo] = useState(true);
  const [confirmou, setConfirmou] = useState(false);
  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");

  function editar(empresa: Cliente) {
    setOriginal({ ...empresa });
    setNome(empresa.nome);
    setAtivo(empresa.ativo);
    setConfirmou(false);
    setErro("");
    setSucesso("");
  }

  async function salvar(event: FormEvent) {
    event.preventDefault();
    if (ocupado || !original) return;
    if (original.ativo && !ativo && !confirmou) {
      setErro("Confirme a desativação da empresa antes de salvar.");
      return;
    }
    aoOcupar(true);
    setErro("");
    setSucesso("");
    try {
      const resultado = await atualizarEmpresaAdmin(
        original,
        nome.trim(),
        ativo,
      );
      aoAtualizar(resultado);
      setOriginal(null);
      setSucesso(
        `Cadastro de ${resultado.nome} atualizado. Status: ${resultado.ativo ? "ativo" : "inativo"}.`,
      );
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Não foi possível alterar a empresa.",
      );
    } finally {
      aoOcupar(false);
    }
  }

  return (
    <section aria-labelledby="empresas-cadastradas">
      <h2 id="empresas-cadastradas">
        Empresas cadastradas ({empresas.length})
      </h2>
      <p>
        Empresas inativas permanecem cadastradas. A desativação bloqueia o
        acesso financeiro à empresa e preserva seus lançamentos.
      </p>
      <button type="button" disabled={ocupado} onClick={aoRecarregar}>
        Atualizar lista de empresas
      </button>
      {erro && (
        <p role="alert" className="ar-error">
          {erro}
        </p>
      )}
      {sucesso && (
        <p role="status" className="ar-success">
          {sucesso}
        </p>
      )}
      <div className="ar-table">
        <table>
          <thead>
            <tr>
              <th>Empresa</th>
              <th>ID</th>
              <th>Status</th>
              <th>Ação</th>
            </tr>
          </thead>
          <tbody>
            {empresas.map((empresa) => (
              <tr key={empresa.id}>
                <td>{empresa.nome}</td>
                <td>#{empresa.id}</td>
                <td>{empresa.ativo ? "Ativa" : "Inativa"}</td>
                <td>
                  <button
                    type="button"
                    disabled={ocupado}
                    onClick={() => editar(empresa)}
                    aria-label={`Editar empresa ${empresa.nome}`}
                  >
                    Editar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!empresas.length && <p>Nenhuma empresa cadastrada.</p>}
      {original && (
        <form onSubmit={(event) => void salvar(event)}>
          <h3>Editar empresa #{original.id}</h3>
          <label>
            Nome da empresa
            <input
              required
              maxLength={150}
              disabled={ocupado}
              value={nome}
              onChange={(event) => setNome(event.target.value)}
            />
          </label>
          <label>
            Status
            <select
              disabled={ocupado}
              value={ativo ? "ativo" : "inativo"}
              onChange={(event) => {
                setAtivo(event.target.value === "ativo");
                setConfirmou(false);
              }}
            >
              <option value="ativo">Ativa</option>
              <option value="inativo">Inativa</option>
            </select>
          </label>
          {original.ativo && !ativo && (
            <label className="ar-admin-note">
              <span>
                Ao desativar, os usuários vinculados deixam de acessar os dados
                financeiros dessa empresa. Os cadastros, lançamentos e conexões
                persistidas são preservados.
              </span>
              <select
                required
                disabled={ocupado}
                value={confirmou ? "sim" : ""}
                onChange={(event) => setConfirmou(event.target.value === "sim")}
              >
                <option value="">Confirme a desativação</option>
                <option value="sim">
                  Confirmo a desativação desta empresa
                </option>
              </select>
            </label>
          )}
          <button type="submit" disabled={ocupado}>
            {ocupado ? "Salvando…" : "Salvar alterações da empresa"}
          </button>
          <button
            type="button"
            disabled={ocupado}
            onClick={() => {
              setOriginal(null);
              setErro("");
            }}
          >
            Cancelar edição
          </button>
        </form>
      )}
    </section>
  );
}
