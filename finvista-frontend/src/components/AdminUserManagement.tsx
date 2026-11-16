import { useState, type FormEvent } from "react";
import {
  atualizarUsuarioAdmin,
  excluirUsuarioAdmin,
  type AdminUser,
} from "../services/adminRegistrationService";
import type { Cliente } from "../services/clientService";

type Props = {
  empresas: Cliente[];
  usuarios: AdminUser[];
  ocupado: boolean;
  usuarioAtualId?: number;
  aoOcupar: (v: boolean) => void;
  aoAtualizar: (u: AdminUser) => void;
  aoRecarregar: () => void;
};
export default function AdminUserManagement({
  usuarios,
  empresas,
  ocupado,
  usuarioAtualId,
  aoOcupar,
  aoAtualizar,
  aoRecarregar,
}: Props) {
  const [original, setOriginal] = useState<AdminUser | null>(null),
    [remover, setRemover] = useState<AdminUser | null>(null);
  const [nome, setNome] = useState(""),
    [email, setEmail] = useState(""),
    [ativo, setAtivo] = useState(true),
    [reset, setReset] = useState(false),
    [senha, setSenha] = useState(""),
    [confirmar, setConfirmar] = useState(""),
    [confirmou, setConfirmou] = useState(false),
    [textoExclusao, setTextoExclusao] = useState(""),
    [erro, setErro] = useState(""),
    [sucesso, setSucesso] = useState("");
  const [empresasAdicionais, setEmpresasAdicionais] = useState<number[]>([]);
  function fechar() {
    setOriginal(null);
    setRemover(null);
    setSenha("");
    setConfirmar("");
    setReset(false);
    setConfirmou(false);
    setTextoExclusao("");
  }
  function editar(u: AdminUser) {
    fechar();
    setOriginal({ ...u });
    setNome(u.nome);
    setEmail(u.email);
    setAtivo(u.ativo);
    setEmpresasAdicionais(u.empresasAdicionais ?? []);
    setErro("");
    setSucesso("");
  }
  async function salvar(e: FormEvent) {
    e.preventDefault();
    if (!original || ocupado) return;
    setErro("");
    setSucesso("");
    if (!original.revisao) {
      setErro("Atualize a lista após publicar o backend desta atualização.");
      return;
    }
    if (reset && (senha.length < 8 || senha !== confirmar)) {
      setErro(
        "Confira a nova senha e a confirmação; use pelo menos 8 caracteres.",
      );
      return;
    }
    if (original.ativo && !ativo && !confirmou) {
      setErro("Confirme a desativação do acesso.");
      return;
    }
    aoOcupar(true);
    try {
      const result = await atualizarUsuarioAdmin(
        original,
        nome.trim(),
        email.trim(),
        ativo,
        reset ? senha : null,
        reset ? confirmar : null,
        empresasAdicionais,
      );
      aoAtualizar(result);
      setSucesso(
        "Usuário atualizado. Alterações de e-mail, senha, status ou vínculos exigem novo login nas sessões anteriores.",
      );
      fechar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível salvar.");
    } finally {
      aoOcupar(false);
    }
  }
  async function excluir(e: FormEvent) {
    e.preventDefault();
    if (!remover || ocupado || textoExclusao !== remover.email) return;
    aoOcupar(true);
    setErro("");
    try {
      await excluirUsuarioAdmin(remover);
      fechar();
      setSucesso("Usuário sem vínculos excluído.");
      aoRecarregar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível excluir.");
    } finally {
      aoOcupar(false);
    }
  }
  return (
    <section aria-labelledby="usuarios-cadastrados">
      <h2 id="usuarios-cadastrados">
        Usuários cadastrados ({usuarios.length})
      </h2>
      <p>
        O perfil e a empresa vinculada são preservados nesta edição. Usuários
        com histórico podem ser desativados.
      </p>
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
              <th>Nome</th>
              <th>E-mail</th>
              <th>Perfil</th>
              <th>Empresa</th>
              <th>Status</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody>
            {usuarios.map((u) => (
              <tr key={u.id}>
                <td>{u.nome}</td>
                <td>{u.email}</td>
                <td>{u.perfil}</td>
                <td>
                  {u.clienteNome ??
                    (u.perfil === "ADMIN"
                      ? "Carteira de clientes"
                      : "Sem vínculo")}
                  {u.clienteId != null ? ` · #${u.clienteId}` : ""}
                </td>
                <td>{u.ativo ? "Ativo" : "Inativo"}</td>
                <td>
                  <button
                    type="button"
                    disabled={ocupado}
                    onClick={() => editar(u)}
                  >
                    Editar acesso
                  </button>{" "}
                  <button
                    type="button"
                    disabled={ocupado || u.id === usuarioAtualId || !u.revisao}
                    onClick={() => {
                      fechar();
                      setRemover({ ...u });
                      setErro("");
                      setSucesso("");
                    }}
                  >
                    Excluir
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!usuarios.length && <p>Nenhum usuário encontrado.</p>}
      {original && (
        <form className="ar-access-form" onSubmit={(e) => void salvar(e)}>
          <h3>Editar acesso de {original.nome}</h3>
          <p>
            Perfil: {original.perfil} · Empresa:{" "}
            {original.clienteNome ?? "Carteira de clientes"}
            {original.clienteId != null ? ` · #${original.clienteId}` : ""}
          </p>
          {
            <fieldset className="ar-company-links" disabled={ocupado}>
              <legend>Empresas vinculadas ao seletor</legend>
              {original.clienteId != null && (
                <div className="ar-company-primary">
                  <strong>{original.clienteNome ?? `Empresa #${original.clienteId}`}</strong>
                  <span>Empresa principal{empresas.some((empresa) =>
                    empresa.id === original.clienteId && !empresa.ativo
                  ) ? " · Inativa" : ""}</span>
                </div>
              )}
              {empresas
                .filter(
                  (empresa) =>
                    empresa.id !== original.clienteId &&
                    (empresa.ativo || empresasAdicionais.includes(empresa.id)),
                )
                .map((empresa) => (
                  <label key={empresa.id} className="ar-checkbox-row">
                    <input
                      type="checkbox"
                      checked={empresasAdicionais.includes(empresa.id)}
                      onChange={(event) => {
                        const marcado = event.target.checked;
                        setEmpresasAdicionais((ids) =>
                          marcado
                            ? [...ids, empresa.id]
                            : ids.filter((id) => id !== empresa.id),
                        );
                      }}
                    />
                    <span>{empresa.nome}{empresa.ativo ? "" : " (inativa)"}</span>
                  </label>
                ))}
            </fieldset>
          }
          <label>
            Nome
            <input
              required
              maxLength={120}
              value={nome}
              disabled={ocupado}
              onChange={(e) => setNome(e.target.value)}
            />
          </label>
          <label>
            E-mail de acesso
            <input
              required
              type="email"
              maxLength={180}
              autoComplete="off"
              value={email}
              disabled={ocupado}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label>
            Status
            <select
              value={ativo ? "ativo" : "inativo"}
              disabled={ocupado || original.id === usuarioAtualId}
              onChange={(e) => setAtivo(e.target.value === "ativo")}
            >
              <option value="ativo">Ativo</option>
              <option value="inativo">Inativo</option>
            </select>
          </label>
          {original.ativo && !ativo && (
            <label className="ar-checkbox-row">
              <input
                type="checkbox"
                required
                checked={confirmou}
                disabled={ocupado}
                onChange={(e) => setConfirmou(e.target.checked)}
              />
              Confirmo a desativação do acesso deste usuário
            </label>
          )}
          <label className="ar-checkbox-row">
            <input
              type="checkbox"
              checked={reset}
              disabled={ocupado}
              onChange={(e) => {
                setReset(e.target.checked);
                setSenha("");
                setConfirmar("");
              }}
            />
            Redefinir a senha
          </label>
          {reset && (
            <>
              <label>
                Nova senha
                <input
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={8}
                  maxLength={72}
                  value={senha}
                  disabled={ocupado}
                  onChange={(e) => setSenha(e.target.value)}
                />
              </label>
              <label>
                Confirmar nova senha
                <input
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={8}
                  maxLength={72}
                  value={confirmar}
                  disabled={ocupado}
                  onChange={(e) => setConfirmar(e.target.value)}
                />
              </label>
              <p>
                Use pelo menos 8 caracteres, até 72 bytes em UTF-8. As sessões
                anteriores precisarão entrar novamente.
              </p>
            </>
          )}
          <button type="submit" disabled={ocupado}>
            Salvar acesso
          </button>
          <button type="button" disabled={ocupado} onClick={fechar}>
            Cancelar
          </button>
        </form>
      )}
      {remover && (
        <form className="ar-access-form" onSubmit={(e) => void excluir(e)}>
          <h3>Excluir usuário {remover.nome}</h3>
          <p>
            A exclusão é definitiva e só é permitida sem histórico ou vínculos.
            Digite o e-mail {remover.email} para confirmar.
          </p>
          <label>
            Confirmar e-mail
            <input
              value={textoExclusao}
              disabled={ocupado}
              onChange={(e) => setTextoExclusao(e.target.value)}
              autoComplete="off"
            />
          </label>
          <button
            type="submit"
            disabled={ocupado || textoExclusao !== remover.email}
          >
            Confirmar exclusão
          </button>
          <button type="button" disabled={ocupado} onClick={fechar}>
            Cancelar
          </button>
        </form>
      )}
    </section>
  );
}
