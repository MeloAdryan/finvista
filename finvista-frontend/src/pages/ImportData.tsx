import {
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
} from 'react'

import {
  importarCsv,
  importarExcel,
  previewCsv,
  previewExcel,
  type FinancialTransaction,
  type ImportResult,
} from '../services/importService'

import '../styles/import-data.css'

type EstadoImportacao =
  | 'inicial'
  | 'arquivo-selecionado'
  | 'analisando'
  | 'preview'
  | 'importando'
  | 'concluido'

function ImportData() {
  const inputRef = useRef<HTMLInputElement>(null)

  const [arquivo, setArquivo] =
    useState<File | null>(null)

  const [estado, setEstado] =
    useState<EstadoImportacao>('inicial')

  const [lancamentos, setLancamentos] =
    useState<FinancialTransaction[]>([])

  const [resultado, setResultado] =
    useState<ImportResult | null>(null)

  const [erro, setErro] =
    useState<string | null>(null)

  const [arrastando, setArrastando] =
    useState(false)

  function extensaoArquivo(file: File) {
    return file.name
      .split('.')
      .pop()
      ?.toLowerCase()
  }

  function arquivoPermitido(file: File) {
    const extensao = extensaoArquivo(file)

    return (
      extensao === 'csv' ||
      extensao === 'xlsx' ||
      extensao === 'xls'
    )
  }

  function selecionarArquivo(file: File) {
    setErro(null)
    setResultado(null)
    setLancamentos([])

    if (!arquivoPermitido(file)) {
      setArquivo(null)
      setEstado('inicial')
      setErro(
        'Formato não suportado. Selecione um arquivo CSV ou Excel.',
      )
      return
    }

    setArquivo(file)
    setEstado('arquivo-selecionado')
  }

  function handleInputChange(
    event: ChangeEvent<HTMLInputElement>,
  ) {
    const file = event.target.files?.[0]

    if (file) {
      selecionarArquivo(file)
    }
  }

  function handleDrop(
    event: DragEvent<HTMLDivElement>,
  ) {
    event.preventDefault()
    setArrastando(false)

    const file = event.dataTransfer.files?.[0]

    if (file) {
      selecionarArquivo(file)
    }
  }

  async function analisarArquivo() {
    if (!arquivo) {
      return
    }

    try {
      setEstado('analisando')
      setErro(null)

      const extensao =
        extensaoArquivo(arquivo)

      const resposta =
        extensao === 'csv'
          ? await previewCsv(arquivo)
          : await previewExcel(arquivo)

      setLancamentos(
        resposta.lancamentos ?? [],
      )

      setEstado('preview')
    } catch (error) {
      console.error(
        'Erro ao analisar arquivo:',
        error,
      )

      setEstado('arquivo-selecionado')

      setErro(
        error instanceof Error
          ? error.message
          : 'Não foi possível analisar o arquivo.',
      )
    }
  }

  async function confirmarImportacao() {
    if (!arquivo) {
      return
    }

    try {
      setEstado('importando')
      setErro(null)

      const extensao =
        extensaoArquivo(arquivo)

      const resposta =
        extensao === 'csv'
          ? await importarCsv(arquivo)
          : await importarExcel(arquivo)

      setResultado(resposta)
      setLancamentos(
        resposta.lancamentos ?? [],
      )

      setEstado('concluido')
    } catch (error) {
      console.error(
        'Erro ao importar arquivo:',
        error,
      )

      setEstado('preview')

      setErro(
        error instanceof Error
          ? error.message
          : 'Não foi possível importar o arquivo.',
      )
    }
  }

  function limparImportacao() {
    setArquivo(null)
    setLancamentos([])
    setResultado(null)
    setErro(null)
    setEstado('inicial')

    if (inputRef.current) {
      inputRef.current.value = ''
    }
  }

  function formatarTamanho(bytes: number) {
    if (bytes < 1024) {
      return `${bytes} B`
    }

    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`
    }

    return `${(
      bytes /
      (1024 * 1024)
    ).toFixed(1)} MB`
  }

  function formatarMoeda(valor: number) {
    return Number(valor).toLocaleString(
      'pt-BR',
      {
        style: 'currency',
        currency: 'BRL',
      },
    )
  }

  return (
    <div
      id="importacao"
      className="import-data"
    >
      <header className="import-data-header">
        <div>
          <span className="import-data-eyebrow">
            DADOS FINANCEIROS
          </span>

          <h1>Importação de dados</h1>

          <p>
            Importe lançamentos financeiros
            através de arquivos CSV ou Excel.
          </p>
        </div>

        <div className="import-data-status">
          <span className="import-data-status-dot" />

          <div>
            <small>DESTINO</small>
            <strong>FinVista</strong>
          </div>
        </div>
      </header>

      <section className="import-card">
        <div
          className={`import-dropzone ${
            arrastando
              ? 'import-dropzone-active'
              : ''
          }`}
          onDragOver={(event) => {
            event.preventDefault()
            setArrastando(true)
          }}
          onDragLeave={() =>
            setArrastando(false)
          }
          onDrop={handleDrop}
        >
          <div className="import-upload-icon">
            ↑
          </div>

          <h2>
            Arraste seu arquivo aqui
          </h2>

          <p>
            ou selecione um arquivo do
            computador
          </p>

          <input
            ref={inputRef}
            type="file"
            accept=".csv,.xlsx,.xls"
            onChange={handleInputChange}
            className="import-file-input"
          />

          <button
            type="button"
            className="import-select-button"
            onClick={() =>
              inputRef.current?.click()
            }
          >
            Selecionar arquivo
          </button>

          <span className="import-formats">
            CSV • Excel (.xlsx / .xls)
          </span>
        </div>

        {erro && (
          <div className="import-message import-message-error">
            <strong>
              Não foi possível continuar
            </strong>

            <span>{erro}</span>
          </div>
        )}

        {arquivo && (
          <div className="import-selected-file">
            <div className="import-file-icon">
              {extensaoArquivo(arquivo)
                ?.toUpperCase()}
            </div>

            <div className="import-file-info">
              <strong>
                {arquivo.name}
              </strong>

              <span>
                {formatarTamanho(
                  arquivo.size,
                )}
              </span>
            </div>

            <button
              type="button"
              className="import-remove-button"
              onClick={limparImportacao}
              disabled={
                estado === 'analisando' ||
                estado === 'importando'
              }
            >
              Remover
            </button>
          </div>
        )}

        {arquivo &&
          estado ===
            'arquivo-selecionado' && (
            <div className="import-actions">
              <button
                type="button"
                className="import-primary-button"
                onClick={analisarArquivo}
              >
                Analisar arquivo
              </button>
            </div>
          )}

        {estado === 'analisando' && (
          <div className="import-processing">
            <span className="import-spinner" />

            <div>
              <strong>
                Analisando arquivo
              </strong>

              <p>
                Validando os lançamentos
                financeiros...
              </p>
            </div>
          </div>
        )}
      </section>

      {(estado === 'preview' ||
        estado === 'importando') && (
        <section className="import-preview-card">
          <div className="import-preview-header">
            <div>
              <span className="import-data-eyebrow">
                PRÉ-VISUALIZAÇÃO
              </span>

              <h2>
                Lançamentos encontrados
              </h2>

              <p>
                Confira os dados antes de
                confirmar a importação.
              </p>
            </div>

            <div className="import-preview-count">
              <strong>
                {lancamentos.length}
              </strong>

              <span>registros</span>
            </div>
          </div>

          <div className="import-table-wrapper">
            <table className="import-table">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Descrição</th>
                  <th>Tipo</th>
                  <th>Categoria</th>
                  <th>Centro de custo</th>
                  <th>Valor</th>
                </tr>
              </thead>

              <tbody>
                {lancamentos
                  .slice(0, 20)
                  .map(
                    (
                      lancamento,
                      index,
                    ) => (
                      <tr
                        key={`${lancamento.descricao}-${index}`}
                      >
                        <td>
                          {lancamento.data}
                        </td>

                        <td>
                          {
                            lancamento.descricao
                          }
                        </td>

                        <td>
                          <span
                            className={`import-type-badge ${
                              lancamento.tipo ===
                              'RECEITA'
                                ? 'import-type-revenue'
                                : 'import-type-expense'
                            }`}
                          >
                            {lancamento.tipo}
                          </span>
                        </td>

                        <td>
                          {lancamento.categoria ??
                            '—'}
                        </td>

                        <td>
                          {lancamento.centroCusto ??
                            '—'}
                        </td>

                        <td className="import-value">
                          {formatarMoeda(
                            lancamento.valor,
                          )}
                        </td>
                      </tr>
                    ),
                  )}
              </tbody>
            </table>
          </div>

          {lancamentos.length > 20 && (
            <p className="import-table-note">
              Exibindo os primeiros 20 de{' '}
              {lancamentos.length} registros.
            </p>
          )}

          <div className="import-actions">
            <button
              type="button"
              className="import-secondary-button"
              onClick={limparImportacao}
              disabled={
                estado === 'importando'
              }
            >
              Cancelar
            </button>

            <button
              type="button"
              className="import-primary-button"
              onClick={
                confirmarImportacao
              }
              disabled={
                estado === 'importando'
              }
            >
              {estado === 'importando'
                ? 'Importando...'
                : 'Confirmar importação'}
            </button>
          </div>
        </section>
      )}

      {estado === 'concluido' &&
        resultado && (
          <section className="import-result-card">
            <div className="import-success-icon">
              ✓
            </div>

            <div className="import-result-content">
              <span className="import-data-eyebrow">
                IMPORTAÇÃO CONCLUÍDA
              </span>

              <h2>
                Dados importados com sucesso
              </h2>

              <p>
                O arquivo{' '}
                <strong>
                  {resultado.arquivo}
                </strong>{' '}
                foi processado pelo FinVista.
              </p>

              <div className="import-result-grid">
                <div>
                  <span>Processados</span>
                  <strong>
                    {resultado.processados}
                  </strong>
                </div>

                <div>
                  <span>Importados</span>
                  <strong>
                    {resultado.importados}
                  </strong>
                </div>

                <div>
                  <span>
                    Duplicidades ignoradas
                  </span>
                  <strong>
                    {
                      resultado.ignoradosDuplicidade
                    }
                  </strong>
                </div>
              </div>

              <button
                type="button"
                className="import-primary-button"
                onClick={limparImportacao}
              >
                Importar outro arquivo
              </button>
            </div>
          </section>
        )}
    </div>
  )
}

export default ImportData