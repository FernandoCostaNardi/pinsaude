import type { FrequenciaMedicaResp } from '../api/frequenciasApi'

export interface FrequenciaPdfParams {
  freq: FrequenciaMedicaResp
  medicoNome: string
  medicoCrm: string
  medicoCrmUf: string
  tomadorNome: string
  empresaNome: string
  empresaCnpj: string
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const MESES_EXT = [
  'JANEIRO', 'FEVEREIRO', 'MARÇO', 'ABRIL', 'MAIO', 'JUNHO',
  'JULHO', 'AGOSTO', 'SETEMBRO', 'OUTUBRO', 'NOVEMBRO', 'DEZEMBRO',
]

function competenciaPorExtenso(competencia: string): string {
  const [ano, mes] = competencia.split('-')
  return `${MESES_EXT[parseInt(mes, 10) - 1]} DE ${ano}`
}

function formatDataCurta(iso: string): string {
  // iso = "2026-07-05" → "05/07/26"
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y.slice(2)}`
}

function formatCnpj(cnpj: string): string {
  const digits = cnpj.replace(/\D/g, '')
  if (digits.length === 14) {
    return digits.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5')
  }
  return cnpj
}

function gerarDataHoraAtual(): string {
  return new Date().toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

// ─── Geração do HTML ──────────────────────────────────────────────────────────

function buildHtml(p: FrequenciaPdfParams): string {
  const { freq, medicoNome, medicoCrm, medicoCrmUf, tomadorNome, empresaNome, empresaCnpj } = p
  const competenciaExt = competenciaPorExtenso(freq.competencia)
  const cnpjFormatado  = formatCnpj(empresaCnpj)

  const linhasPreenchidas = freq.itens
    .slice()
    .sort((a, b) => a.dataExecucao < b.dataExecucao ? -1 : 1)
    .map(item => `
      <tr>
        <td style="text-align:center">${formatDataCurta(item.dataExecucao)}</td>
        <td style="text-align:center">${item.modalidadeTurno ?? ''}</td>
        <td style="text-align:center">${item.modalidadeHorario ?? ''}</td>
        <td></td>
        <td>${item.ocorrencia ?? ''}</td>
        <td></td>
      </tr>
    `).join('')

  // Linhas em branco para preenchimento manual (mínimo 20 linhas visíveis no total)
  const totalLinhas = Math.max(20, freq.itens.length + 5)
  const linhasVazias = Array.from({ length: totalLinhas - freq.itens.length }, () => `
    <tr>
      <td></td><td></td><td></td><td></td><td></td><td></td>
    </tr>
  `).join('')

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <title>Relatório de Frequência Médica Individual</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: Arial, Helvetica, sans-serif;
      font-size: 9pt;
      color: #000;
      background: #fff;
    }
    .page {
      width: 210mm;
      min-height: 297mm;
      padding: 10mm 12mm 12mm 12mm;
      margin: 0 auto;
    }

    /* === Header Governo === */
    .gov-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding-bottom: 6px;
      border-bottom: 2.5px solid #333;
      margin-bottom: 8px;
    }
    .gov-brasao {
      font-size: 36pt;
      line-height: 1;
      flex-shrink: 0;
    }
    .gov-text h1 { font-size: 10.5pt; font-weight: bold; letter-spacing: 0.5px; }
    .gov-text h2 { font-size: 9pt; font-weight: normal; letter-spacing: 0.3px; }
    .gov-text h3 { font-size: 8pt; color: #555; margin-top: 1px; }

    /* === Título do Formulário === */
    .form-title {
      text-align: center;
      font-size: 11.5pt;
      font-weight: bold;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      border: 2px solid #000;
      padding: 5px 10px;
      margin: 8px 0;
      background: #f2f2f2;
    }

    /* === Campos do Formulário === */
    .fields-section {
      border: 1px solid #000;
      margin-bottom: 6px;
    }
    .field-row {
      display: flex;
      border-bottom: 1px solid #000;
      min-height: 24px;
      align-items: stretch;
    }
    .field-row:last-child { border-bottom: none; }
    .field-cell {
      padding: 3px 6px;
      border-right: 1px solid #000;
      flex: 1;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
    .field-cell:last-child { border-right: none; }
    .field-cell.w2 { flex: 2; }
    .field-cell.w3 { flex: 3; }
    .field-label {
      font-size: 6.5pt;
      font-weight: bold;
      text-transform: uppercase;
      color: #555;
      letter-spacing: 0.2px;
      display: block;
      margin-bottom: 1px;
    }
    .field-value {
      font-size: 9pt;
      font-weight: 600;
      display: block;
    }

    /* === Seção título === */
    .section-bar {
      font-size: 8pt;
      font-weight: bold;
      text-transform: uppercase;
      background: #444;
      color: #fff;
      padding: 3px 6px;
      letter-spacing: 0.5px;
      margin-bottom: 0;
    }

    /* === Tabela de Plantões === */
    table {
      width: 100%;
      border-collapse: collapse;
    }
    thead tr { background: #e0e0e0; }
    th {
      border: 1px solid #000;
      padding: 4px 3px;
      font-size: 7.5pt;
      font-weight: bold;
      text-align: center;
      text-transform: uppercase;
      white-space: nowrap;
    }
    td {
      border: 1px solid #aaa;
      padding: 2.5px 4px;
      font-size: 8.5pt;
      height: 17px;
      vertical-align: middle;
    }
    tbody tr:nth-child(even) { background: #fafafa; }
    .col-data    { width: 55px; }
    .col-turno   { width: 70px; }
    .col-horario { width: 105px; }
    .col-rubrica { width: 120px; }
    .col-lider   { width: 45px; }

    /* === Totalizador === */
    .totalizador {
      margin-top: 6px;
      font-size: 8.5pt;
      text-align: right;
      font-weight: bold;
    }

    /* === Assinaturas === */
    .signatures {
      display: flex;
      gap: 20px;
      margin-top: 28px;
      padding-top: 4px;
    }
    .sig-box {
      flex: 1;
      text-align: center;
    }
    .sig-space { height: 38px; }
    .sig-line {
      border-top: 1px solid #000;
      margin: 0 10px 3px 10px;
    }
    .sig-label {
      font-size: 7.5pt;
      font-weight: bold;
      text-transform: uppercase;
      letter-spacing: 0.3px;
    }
    .sig-sub {
      font-size: 7pt;
      color: #555;
      margin-top: 2px;
    }

    /* === Rodapé === */
    .footer {
      margin-top: 14px;
      font-size: 7pt;
      color: #888;
      text-align: center;
      border-top: 1px solid #ccc;
      padding-top: 4px;
    }

    @media print {
      html, body { margin: 0; padding: 0; background: #fff; }
      .page { margin: 0; padding: 8mm 10mm 10mm 10mm; width: 100%; }
      @page {
        size: A4 portrait;
        margin: 0;
      }
    }
  </style>
</head>
<body>
<div class="page">

  <!-- Header institucional -->
  <div class="gov-header">
    <div class="gov-brasao">⚖</div>
    <div class="gov-text">
      <h1>GOVERNO DO ESTADO DE PERNAMBUCO</h1>
      <h2>Secretaria de Saúde de Pernambuco — SES/PE</h2>
      <h3>Gestão de Recursos Humanos em Saúde</h3>
    </div>
  </div>

  <!-- Título -->
  <div class="form-title">Relatório de Frequência Médica Individual</div>

  <!-- Campos -->
  <div class="fields-section">
    <div class="field-row">
      <div class="field-cell w3">
        <span class="field-label">Unidade de Saúde</span>
        <span class="field-value">${tomadorNome}</span>
      </div>
      <div class="field-cell w2">
        <span class="field-label">Empresa Prestadora</span>
        <span class="field-value">${empresaNome}${cnpjFormatado ? ' — CNPJ: ' + cnpjFormatado : ''}</span>
      </div>
    </div>
    <div class="field-row">
      <div class="field-cell w3">
        <span class="field-label">Nome do(a) Médico(a)</span>
        <span class="field-value">${medicoNome}</span>
      </div>
      <div class="field-cell w2">
        <span class="field-label">Identificação Funcional (CRM)</span>
        <span class="field-value">${medicoCrm}/${medicoCrmUf}</span>
      </div>
    </div>
    <div class="field-row">
      <div class="field-cell">
        <span class="field-label">Competência</span>
        <span class="field-value">${competenciaExt}</span>
      </div>
      <div class="field-cell w2">
        <span class="field-label">Especialidade Médica</span>
        <span class="field-value">${freq.especialidade}</span>
      </div>
      <div class="field-cell">
        <span class="field-label">Setor</span>
        <span class="field-value">${freq.servicoOperacionalNome ?? ''}</span>
      </div>
    </div>
  </div>

  <!-- Tabela de plantões -->
  <div class="section-bar">Registro de Frequência / Plantões</div>
  <table>
    <thead>
      <tr>
        <th class="col-data">Data</th>
        <th class="col-turno">Turno</th>
        <th class="col-horario">Horário</th>
        <th class="col-rubrica">Rubrica e Carimbo</th>
        <th>Ocorrência</th>
        <th class="col-lider">Líder</th>
      </tr>
    </thead>
    <tbody>
      ${linhasPreenchidas}
      ${linhasVazias}
    </tbody>
  </table>

  <div class="totalizador">
    Total de plantões registrados neste relatório: <span>${freq.itens.length}</span>
  </div>

  <!-- Assinaturas -->
  <div class="signatures">
    <div class="sig-box">
      <div class="sig-space"></div>
      <div class="sig-line"></div>
      <div class="sig-label">Prestador(a)</div>
      <div class="sig-sub">Assinatura e Carimbo</div>
    </div>
    <div class="sig-box">
      <div class="sig-space"></div>
      <div class="sig-line"></div>
      <div class="sig-label">Direção Médica</div>
      <div class="sig-sub">Assinatura e Carimbo</div>
    </div>
    <div class="sig-box">
      <div class="sig-space"></div>
      <div class="sig-line"></div>
      <div class="sig-label">Diretor(a) Adm. Financeiro</div>
      <div class="sig-sub">Assinatura e Carimbo</div>
    </div>
  </div>

  <!-- Rodapé -->
  <div class="footer">
    Gerado pelo Sistema Pin Saúde · ${gerarDataHoraAtual()} ·
    Competência: ${competenciaExt} · ${freq.itens.length} plantão(ões)
  </div>

</div>
<script>
  window.onload = function() {
    // Pequeno delay para o navegador renderizar completamente antes de imprimir
    setTimeout(function() { window.print(); }, 300);
  };
</script>
</body>
</html>`
}

// ─── API pública ──────────────────────────────────────────────────────────────

export function abrirPdfFrequencia(params: FrequenciaPdfParams): void {
  const html = buildHtml(params)
  const win = window.open('', '_blank', 'width=900,height=1100,scrollbars=yes')
  if (!win) {
    alert('Habilite pop-ups para gerar o PDF desta frequência.')
    return
  }
  win.document.write(html)
  win.document.close()
}
