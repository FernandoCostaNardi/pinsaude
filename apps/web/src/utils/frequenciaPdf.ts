import type { FrequenciaMedicaResp, FrequenciaItemResp } from '../api/frequenciasApi'

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

const DIAS_SEMANA = [
  'DOMINGO', 'SEGUNDA-FEIRA', 'TERÇA-FEIRA', 'QUARTA-FEIRA',
  'QUINTA-FEIRA', 'SEXTA-FEIRA', 'SÁBADO',
]

function competenciaPorExtenso(competencia: string): string {
  const [ano, mes] = competencia.split('-')
  return `${MESES_EXT[parseInt(mes, 10) - 1]} DE ${ano}`
}

function formatDataCurta(iso: string): string {
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

// PINSAUDE-13.25: modalidade Diarista não tem turno/horário cadastrados (paga valor mensal
// fixo) — o horário impresso no PDF é o que o médico digitou naquele lançamento específico
// (horaInicio/horaFim), nunca um valor da modalidade. Plantonista continua usando
// modalidadeTurno/modalidadeHorario, cadastrados uma única vez na modalidade.
function formatHorarioItem(item: FrequenciaItemResp): string | null {
  if (!item.horaInicio || !item.horaFim) return null
  return `${item.horaInicio.slice(0, 5)} às ${item.horaFim.slice(0, 5)}`
}

function gerarOcorrencia(item: FrequenciaItemResp): string {
  if (item.ocorrencia) return item.ocorrencia  // preserva valor informado manualmente

  const [y, m, d] = item.dataExecucao.split('-').map(Number)
  const diaSemana = DIAS_SEMANA[new Date(y, m - 1, d).getDay()]

  const turno = item.modalidadeTurno
    ? item.modalidadeTurno.charAt(0) + item.modalidadeTurno.slice(1).toLowerCase()
    : ''

  let horasStr = formatHorarioItem(item) ?? ''
  if (!horasStr && item.modalidadeHoras != null) {
    const h = item.modalidadeHoras
    const hFmt = h % 1 === 0 ? String(h) : h.toFixed(1).replace('.', ',')
    horasStr = `${hFmt} hora${h !== 1 ? 's' : ''}`
  }

  return [diaSemana, turno, horasStr].filter(Boolean).join(' - ')
}

// ─── Geração do HTML ──────────────────────────────────────────────────────────

function buildHtml(p: FrequenciaPdfParams): string {
  const { freq, medicoNome, medicoCrm, medicoCrmUf, tomadorNome, empresaNome, empresaCnpj } = p
  const competenciaExt = competenciaPorExtenso(freq.competencia)
  const cnpjFormatado  = formatCnpj(empresaCnpj)
  const logoUrl        = `${window.location.origin}/logo-formulario.png`

  // Mês por extenso apenas (sem o ano) para o campo Competência
  const [anoComp, mesComp] = freq.competencia.split('-')
  const mesExt = MESES_EXT[parseInt(mesComp, 10) - 1]

  const linhasPreenchidas = freq.itens
    .slice()
    .sort((a, b) => a.dataExecucao < b.dataExecucao ? -1 : 1)
    .map(item => `
      <tr>
        <td style="text-align:center">${formatDataCurta(item.dataExecucao)}</td>
        <td style="text-align:center">${item.modalidadeTurno ?? ''}</td>
        <td style="text-align:center">${formatHorarioItem(item) ?? item.modalidadeHorario ?? ''}</td>
        <td></td>
        <td>${gerarOcorrencia(item)}</td>
      </tr>
    `).join('')

  // Linhas em branco calculadas para preencher uma página A4 completa (~35 linhas totais)
  const totalLinhas = Math.max(35, freq.itens.length + 5)
  const linhasVazias = Array.from({ length: totalLinhas - freq.itens.length }, () => `
    <tr>
      <td></td><td></td><td></td><td></td><td></td>
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

    /* === Header Governo (tabela 2 colunas com borda) === */
    .gov-header-table {
      width: 100%;
      border-collapse: collapse;
      border: 1.5px solid #000;
      margin-bottom: 0;
    }
    .gov-header-table td {
      border: none;
      padding: 0;
      vertical-align: middle;
    }
    .gov-logo-cell {
      width: 175px;
      border-right: 1.5px solid #000 !important;
      text-align: center;
      padding: 4px 6px !important;
    }
    .gov-logo-cell img {
      height: 72px;
      display: block;
      margin: 0 auto;
    }
    .gov-text-cell {
      padding: 6px 10px !important;
      vertical-align: middle;
    }
    .gov-text-cell p {
      font-size: 9pt;
      font-weight: bold;
      line-height: 1.45;
      color: #000;
      margin: 0;
    }

    /* === Título do Formulário === */
    .form-title-table {
      width: 100%;
      border-collapse: collapse;
      border: 1.5px solid #000;
      border-top: none;
      margin-bottom: 0;
    }
    .form-title-table td {
      text-align: center;
      font-size: 11pt;
      font-weight: bold;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      padding: 5px 10px;
      border: none;
    }

    /* === Campos do Formulário (tabela de linhas) === */
    .fields-table {
      width: 100%;
      border-collapse: collapse;
      border: 1.5px solid #000;
      border-top: none;
      margin-bottom: 20px;
    }
    .fields-table td {
      border: none;
      border-top: 1px solid #000;
      padding: 0;
      vertical-align: middle;
    }
    .field-label-cell {
      width: 185px;
      border-right: 1px solid #000 !important;
      padding: 3px 6px !important;
      font-size: 8pt;
      font-style: italic;
      color: #000;
      white-space: nowrap;
      text-align: right;
    }
    .field-value-cell {
      padding: 3px 8px !important;
      font-size: 9pt;
      font-weight: bold;
      color: #000;
    }
    .field-value-empresa { color: #000; }
    .field-value-medico  { color: #000; }
    .field-value-normal  { color: #000; }
    .field-value-center  { text-align: center; color: #000; }

    /* Célula do setor na linha Tipo de Escala — sem borda separando de field-value-cell
       (evita a "barra" visual entre Tipo de Escala e Setor) */
    .field-setor-cell {
      padding: 3px 8px !important;
      font-size: 9pt;
      font-weight: bold;
      color: #000;
      white-space: nowrap;
    }

    /* === Tabela de Plantões === */
    table.plantoes {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;  /* garante que col-rubrica e col-ocorrencia dividam igualmente */
    }
    table.plantoes thead tr { background: #e0e0e0; }
    table.plantoes th {
      border: 1px solid #000;
      padding: 4px 3px;
      font-size: 7.5pt;
      font-weight: bold;
      text-align: center;
      text-transform: uppercase;
      white-space: nowrap;
    }
    table.plantoes td {
      border: 1px solid #aaa;
      padding: 2.5px 4px;
      font-size: 8.5pt;
      height: 17px;
      vertical-align: middle;
    }
    table.plantoes tbody tr:nth-child(even) { background: #fafafa; }
    .col-data    { width: 55px; }
    .col-turno   { width: 70px; }
    .col-horario { width: 105px; }
    /* RUBRICA e OCORRÊNCIA dividem igualmente o restante (~33% cada) — sem largura fixa */

    /* === Assinaturas — mesmas colunas da tabela de plantões via colspan === */
    .sig-table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;  /* mesma regra da tabela acima — divisores coincidem */
      border: 1px solid #aaa;
      border-top: none;
      margin-top: 0;
    }
    .sig-table td {
      border: none;
      border-right: 1px solid #aaa;  /* mesma cor das células do tbody */
      text-align: center;
      vertical-align: bottom;
      padding: 0;
    }
    .sig-table td:last-child { border-right: none; }
    /* Espaço de assinatura — linha curta centralizada na base da célula */
    .sig-space-row td {
      height: 55px;
      vertical-align: bottom;
      padding-bottom: 3px;
    }
    .sig-line-inner {
      border-top: 1px solid #333;
      margin: 0 22px;
    }
    .sig-label-row td {
      padding: 2px 6px 4px;
      font-size: 8pt;
      font-weight: normal;
      line-height: 1.3;
      word-wrap: break-word;
      overflow-wrap: break-word;
    }

    /* === Rodapé === */
    .footer {
      margin-top: 14px;
      font-size: 7pt;
      color: #000;
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

  <!-- Header institucional: logo | texto oficial -->
  <table class="gov-header-table">
    <tr>
      <td class="gov-logo-cell">
        <img src="${logoUrl}" alt="Secretaria de Saúde de Pernambuco" />
      </td>
      <td class="gov-text-cell">
        <p>GOVERNO DO ESTADO DE PERNAMBUCO</p>
        <p>SECRETARIA DE SAÚDE DO ESTADO DE PERNAMBUCO</p>
        <p>SECRETARIA EXECUTIVA DE ADMINISTRAÇÃO E FINANÇAS</p>
        <p>DIRETORIA GERAL DE FINANÇAS</p>
        <p>RELATÓRIO DE FREQUÊNCIA MÉDICA</p>
      </td>
    </tr>
  </table>

  <!-- Título do formulário -->
  <table class="form-title-table">
    <tr>
      <td>FREQUÊNCIA MÉDICA INDIVIDUAL</td>
    </tr>
  </table>

  <!-- Campos do formulário -->
  <table class="fields-table">
    <tr>
      <td class="field-label-cell">Unidade de Saúde:</td>
      <td class="field-value-cell field-value-normal" colspan="2">${tomadorNome}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Empresa Prestadora do Serviço:</td>
      <td class="field-value-cell field-value-empresa" colspan="2">${empresaNome} CNPJ: ${cnpjFormatado}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Nome do Médico Prestador:</td>
      <td class="field-value-cell field-value-medico" colspan="2">${medicoNome} CRM-${medicoCrmUf} ${medicoCrm}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Competência:</td>
      <td class="field-value-cell field-value-center" colspan="2">${mesExt} / ${anoComp}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Tipo de Escala:</td>
      <td class="field-value-cell field-value-normal">${freq.tipoMedico ?? ''}</td>
      <td class="field-setor-cell">${freq.servicoOperacionalNome ?? ''}</td>
    </tr>
  </table>

  <!-- Tabela de plantões -->
  <table class="plantoes">
    <colgroup>
      <col class="col-data">
      <col class="col-turno">
      <col class="col-horario">
      <col class="col-rubrica">
      <col>
    </colgroup>
    <thead>
      <tr>
        <th>Data</th>
        <th>Turno</th>
        <th>Horário</th>
        <th>Rubrica e Carimbo</th>
        <th>Ocorrência</th>
      </tr>
    </thead>
    <tbody>
      ${linhasPreenchidas}
      ${linhasVazias}
    </tbody>
  </table>

  <!-- Assinaturas — colgroup replica as colunas da tabela de plantões para alinhar divisores.
       Prestador = Data+Turno (2 col) · Direção Médica = Horário+Rubrica (2 col) · Diretor Adm. = Ocorrência (1 col) -->
  <table class="sig-table">
    <colgroup>
      <col class="col-data">
      <col class="col-turno">
      <col class="col-horario">
      <col class="col-rubrica">
      <col>
    </colgroup>
    <tr class="sig-space-row">
      <td colspan="2"><div class="sig-line-inner"></div></td>
      <td colspan="2"><div class="sig-line-inner"></div></td>
      <td><div class="sig-line-inner"></div></td>
    </tr>
    <tr class="sig-label-row">
      <td colspan="2">Assinatura do Prestador(a)</td>
      <td colspan="2">Assinatura da Direção Médica</td>
      <td>Assinatura do Diretor Administrativo Financeiro da unidade</td>
    </tr>
  </table>

  <!-- Rodapé -->
  <div class="footer">
    Gerado pelo Sistema Pin Saúde · ${gerarDataHoraAtual()} ·
    Competência: ${competenciaExt} · ${freq.itens.length} plantão(ões)
  </div>

</div>
<script>
  window.onload = function() {
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
