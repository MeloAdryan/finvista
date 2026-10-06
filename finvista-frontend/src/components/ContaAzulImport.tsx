import { useEffect, useRef, useState } from 'react'
import type { Cliente } from '../services/clientService'
import { applyContaAzul, conferContaAzul, getCandidate, planContaAzul, SyncHttpError,
  type Candidate, type Conference, type Money, type SyncDecision, type SyncPlan, type SyncResult } from '../services/contaAzulSyncService'
import '../styles/conta-azul-import.css'

const currency = (v: Money) => v === null ? 'Não informado' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const date = (v: string | null) => v ? v.split('-').reverse().join('/') : 'Não informada'
function today() { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Sao_Paulo' }).format(new Date()) }

export default function ContaAzulImport({ cliente }: { cliente: Cliente | null }) {
  const [file, setFile] = useState<File | null>(null)
  const [cut, setCut] = useState(today)
  const [conf, setConf] = useState<Conference | null>(null)
  const [plan, setPlan] = useState<SyncPlan | null>(null)
  const [choices, setChoices] = useState<Record<number, string>>({})
  const [candidates, setCandidates] = useState<Record<number, Candidate>>({})
  const [candidateBusy, setCandidateBusy] = useState<Record<number, boolean>>({})
  const [result, setResult] = useState<SyncResult | null>(null)
  const [busy, setBusy] = useState<'read' | 'write' | null>(null)
  const [error, setError] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const version = useRef(0)
  const abort = useRef(new AbortController())
  useEffect(() => () => { version.current++; abort.current.abort() }, [])

  function invalidate() {
    version.current++; abort.current.abort(); abort.current = new AbortController()
    setConf(null); setPlan(null); setChoices({}); setCandidates({}); setCandidateBusy({})
    setConfirmed(false); setError(''); setResult(null); setPage(0); setSearch('')
  }
  async function analyze() {
    if (!file || !cut || !cliente) return
    invalidate(); const current = version.current; setBusy('read')
    try {
      const conference = await conferContaAzul(file, cut, abort.current.signal)
      if (current !== version.current) return
      setConf(conference)
      if (conference.rejeitadas === 0 && conference.linhasLidas > 0) {
        const next = await planContaAzul(file, cut, abort.current.signal)
        if (current === version.current) setPlan(next)
      }
    } catch (e) {
      if (current === version.current && !(e instanceof DOMException && e.name === 'AbortError'))
        setError(e instanceof Error ? e.message : 'Falha na análise.')
    } finally { if (current === version.current) setBusy(null) }
  }
  async function choose(line: number, value: string) {
    setChoices(prev => ({ ...prev, [line]: value })); setConfirmed(false); setError('')
    if (!value.startsWith('id:')) return
    const id = Number(value.slice(3)); if (candidates[id]) return
    const current = version.current
    setCandidateBusy(prev => ({ ...prev, [id]: true }))
    try {
      const candidate = await getCandidate(id, abort.current.signal)
      if (current === version.current) setCandidates(prev => ({ ...prev, [id]: candidate }))
    } catch (e) {
      if (current === version.current && !(e instanceof DOMException && e.name === 'AbortError'))
        setError(e instanceof Error ? e.message : 'Não foi possível consultar o registro.')
    } finally { if (current === version.current) setCandidateBusy(prev => ({ ...prev, [id]: false })) }
  }
  const decisions: SyncDecision[] = (plan?.itens ?? []).flatMap<SyncDecision>(item => {
    const value = choices[item.linha]
    if (value === 'novo' && item.candidatos.length === 0)
      return [{ linha: item.linha, acao: 'CRIAR' as const, lancamentoId: null }]
    if (value?.startsWith('id:')) {
      const id = Number(value.slice(3))
      if (candidates[id] && item.candidatos.includes(id))
        return [{ linha: item.linha, acao: 'ATUALIZAR' as const, lancamentoId: id }]
    }
    return []
  })
  const ids = decisions.flatMap(d => d.lancamentoId === null ? [] : [d.lancamentoId])
  const repeatedId = new Set(ids).size !== ids.length
  const readingCandidates = Object.values(candidateBusy).some(Boolean)
  async function apply() {
    if (!file || !plan || !confirmed || !decisions.length || repeatedId || readingCandidates || busy) return
    const current = version.current; setBusy('write'); setError('')
    try {
      const next = await applyContaAzul(file, plan, decisions)
      if (current !== version.current) return
      setResult(next); setPlan(null); setChoices({}); setConfirmed(false)
    } catch (e) {
      if (current === version.current) {
        setPlan(null); setChoices({}); setConfirmed(false)
        setError(e instanceof SyncHttpError && e.status === 409
          ? 'Os dados mudaram desde a análise. Gere um novo plano e revise as decisões.'
          : `${e instanceof Error ? e.message : 'Falha na confirmação.'} Gere outro plano para conferir o estado atual antes de tentar novamente.`)
      }
    } finally { if (current === version.current) setBusy(null) }
  }
  const filtered = (conf?.linhas ?? []).filter(line => !search.trim()
    || (/^\d+$/.test(search.trim()) ? line.numero === Number(search) : line.descricao.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())))
  const pages = Math.max(1, Math.ceil(filtered.length / 20))
  const shown = filtered.slice(page * 20, page * 20 + 20)
  const itemByLine = new Map(plan?.itens.map(item => [item.linha, item]))

  return <section className="ca-import" aria-label="Conciliação Conta Azul">
    <header><h2>Conferir contas a pagar e receber</h2><p>Revise o arquivo e escolha quais parcelas deseja criar ou atualizar.</p>
      <p className="ca-destination">Cliente de destino: <strong>{cliente?.nome ?? 'Selecione um cliente'}</strong></p></header>
    <div className="ca-inputs">
      <label>Arquivo Conta Azul<input type="file" accept=".xls,.xlsx" disabled={!!busy} onChange={e => {
        invalidate(); const selected = e.target.files?.[0] ?? null
        if (selected && !/\.xlsx?$/i.test(selected.name)) { setFile(null); setError('Selecione um arquivo XLS ou XLSX.'); return }
        setFile(selected)
      }} /></label>
      <label>Data de corte<input type="date" value={cut} disabled={!!busy} onChange={e => { invalidate(); setCut(e.target.value) }} /></label>
      <button type="button" className="ca-primary" disabled={!file || !cut || !cliente || !!busy} onClick={() => void analyze()}>
        {busy === 'read' ? 'Analisando…' : 'Analisar e gerar plano'}</button>
    </div>
    {busy === 'read' && <button type="button" onClick={() => { invalidate(); setBusy(null) }}>Cancelar análise</button>}
    {file && <p>Arquivo selecionado: <strong>{file.name}</strong></p>}
    {error && <p className="ca-error" role="alert">{error}</p>}
    {result && <div className="ca-result" role="status"><h3>Conciliação registrada — lote {result.loteId}</h3>
      <p>Criadas: {result.criados} · Atualizadas: {result.atualizados} · Mantidas: {result.mantidos} · Pendentes: {result.pendentes}</p>
      <p>Para revisar as demais linhas ou conferir a repetição, gere um novo plano.</p></div>}
    {conf && <>
      <div className="ca-summary">
        <div><span>Linhas</span><strong>{conf.linhasLidas}</strong></div>
        <div><span>Original</span><strong>{currency(conf.valorOriginal)}</strong></div>
        <div><span>Realizado após ajustes</span><strong>{currency(conf.valorTotalRealizado)}</strong></div>
        <div><span>Em aberto após ajustes</span><strong>{currency(conf.valorTotalAberto)}</strong></div>
      </div>
      <p>{conf.aceitas} válidas · {conf.rejeitadas} rejeitadas · {conf.linhasComAvisos} com avisos. Totais referentes às linhas válidas deste arquivo.</p>
      {conf.rejeitadas > 0 && <p className="ca-error">Corrija as linhas rejeitadas na origem e gere outro arquivo antes de confirmar.</p>}
      <label className="ca-search">Buscar descrição ou número da linha<input value={search} disabled={busy === 'write'} onChange={e => { setSearch(e.target.value); setPage(0) }} placeholder="Ex.: 95 ou Financiamento" /></label>
      <div className="ca-scroll" tabIndex={0} aria-label="Tabela de revisão, com rolagem horizontal">
        <table><thead><tr><th>Linha</th><th>Parcela e rateios do arquivo</th><th>Decisão e registro existente</th></tr></thead>
          <tbody>{shown.map(line => {
            const item = itemByLine.get(line.numero), value = choices[line.numero] ?? 'pendente'
            const id = value.startsWith('id:') ? Number(value.slice(3)) : null
            const candidate = id === null ? null : candidates[id]
            const notices = [...new Set([...line.avisos, ...(item?.avisos ?? [])])]
            return <tr key={line.numero}><td>{line.numero}</td><td><strong>{line.descricao}</strong>
              <p>Competência: {date(line.competencia)} · Vencimento: {date(line.vencimento)}</p>
              <p>Original: {currency(line.original)} · Realizado: {currency(line.totalRealizado)} · Aberto: {currency(line.totalAberto)}</p>
              <details><summary>Rateios ({line.rateios.length}) e avisos ({notices.length})</summary>
                <ul>{line.rateios.map(r => <li key={r.bloco}>{r.categoria ?? 'Sem categoria'}: {currency(r.valorCategoria)}<br />
                  {r.centro ?? 'Sem centro'}: {currency(r.valorCentro)}</li>)}</ul>
                {notices.map((notice, i) => <p key={i} className="ca-notice">{notice}</p>)}
                {line.erros.map((notice, i) => <p key={i} className="ca-error">{notice}</p>)}
              </details></td><td>
                <label>Decisão da linha {line.numero}<select value={value} disabled={!item || !!busy} onChange={e => void choose(line.numero, e.target.value)}>
                  <option value="pendente">Deixar pendente</option>
                  {item?.candidatos.length === 0 && <option value="novo">Criar parcela nova</option>}
                  {item?.candidatos.map(id => <option value={`id:${id}`} key={id}>Atualizar registro #{id}</option>)}
                </select></label>
                {item && item.candidatos.length > 1 && <p className="ca-notice">Há mais de um candidato. Revise o registro escolhido.</p>}
                {id !== null && candidateBusy[id] && <p role="status">Consultando registro…</p>}
                {candidate && <div className="ca-candidate"><strong>Registro #{candidate.id}: {candidate.descricao}</strong>
                  <p>{candidate.contraparte ?? 'Contraparte não informada'}</p>
                  <p>Competência: {date(candidate.competencia)} · Vencimento: {date(candidate.vencimento)}</p>
                  <p>Original: {currency(candidate.original)} · Principal realizado: {currency(candidate.realizado)} · Principal aberto: {currency(candidate.aberto)}</p>
                  <p>Situação: {candidate.situacao ?? 'Não informada'} · Referência: {candidate.referencia ?? 'Não informada'}</p>
                  <p>{candidate.categoria ?? 'Sem categoria'} · {candidate.centro ?? 'Sem centro'}</p>
                </div>}
                {id !== null && !candidate && !candidateBusy[id] && <button type="button" disabled={!!busy} onClick={() => void choose(line.numero, value)}>Consultar registro novamente</button>}
                {value === 'novo' && <p className="ca-notice">Confirme que esta parcela ainda não existe. Correções na descrição, competência ou valor podem impedir a correspondência.</p>}
              </td></tr>
          })}</tbody></table>
        {shown.length === 0 && <p>Nenhuma linha encontrada.</p>}
      </div>
      <nav className="ca-pages" aria-label="Paginação da revisão"><button type="button" disabled={page === 0 || busy === 'write'} onClick={() => setPage(p => p - 1)}>Anterior</button>
        <span>Página {page + 1} de {pages} · {filtered.length} linhas</span>
        <button type="button" disabled={page + 1 >= pages || busy === 'write'} onClick={() => setPage(p => p + 1)}>Próxima</button></nav>
      {plan && <footer className="ca-confirm">
        <p>{decisions.length} decisões prontas · {plan.linhas - decisions.length} linhas permanecem pendentes. A busca não limita as decisões selecionadas em outras páginas.</p>
        {repeatedId && <p className="ca-error">O mesmo registro foi escolhido para duas linhas. Corrija antes de confirmar.</p>}
        <label className="ca-check"><input type="checkbox" checked={confirmed} disabled={!!busy || readingCandidates || !decisions.length || repeatedId} onChange={e => setConfirmed(e.target.checked)} />
          Conferi o cliente {cliente?.nome} e as decisões selecionadas.</label>
        <button type="button" className="ca-primary" disabled={!confirmed || !decisions.length || repeatedId || readingCandidates || !!busy} onClick={() => void apply()}>
          {busy === 'write' ? 'Gravando…' : `Confirmar ${decisions.length} decisões`}</button>
      </footer>}
    </>}
  </section>
}
