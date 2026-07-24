import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  User, Home, FileText, ShieldCheck, HeartHandshake, Stethoscope,
  CheckCircle2, Loader2,
} from 'lucide-react'
import { Input, Button, Alert, StepWizard } from '@pinsaude/ui'
import { CpfInput } from '../components/CpfInput'
import { PhoneInput } from '../components/PhoneInput'
import { BancoSelect, BancoAvatar, bancos } from '../components/BancoSelect'
import { MultiFileUploadField, UploadedFileRef } from '../components/MultiFileUploadField'
import {
  CandidaturaPublicaRequest, CandidaturaPublicaResponse, EstadoCivil,
  TipoDocumentoCandidatura, TipoPix, TipoConta, CandidaturaDadosBancariosRequest,
  DeclaracaoLgpdRequest, candidaturaMedicoApi,
} from '../api/candidaturaMedicoApi'
import { isValidCpf, formatCpf } from '../utils/cpf'

// ─── Constantes ───────────────────────────────────────────────────────────────

const STORAGE_KEY_ID = 'pinsaude_candidatura_id'
const LAST_STEP = 5

const STEPS = [
  { label: 'Dados Pessoais',           icon: User },
  { label: 'Contato e Endereço',       icon: Home },
  { label: 'Documentos Profissionais', icon: FileText },
  { label: 'Dados Bancários',          icon: HeartHandshake },
  { label: 'Formação',                 icon: Stethoscope },
  { label: 'LGPD',                     icon: ShieldCheck },
]

const UFS = [
  'AC','AL','AM','AP','BA','CE','DF','ES','GO','MA',
  'MG','MS','MT','PA','PB','PE','PI','PR','RJ','RN',
  'RO','RR','RS','SC','SE','SP','TO',
]

const ESTADO_CIVIL_OPTIONS: { value: EstadoCivil; label: string }[] = [
  { value: 'SOLTEIRO',                     label: 'Solteiro(a)' },
  { value: 'CASADO_COMUNHAO_PARCIAL',      label: 'Casado(a) — Comunhão Parcial de Bens' },
  { value: 'CASADO_SEPARACAO_TOTAL',       label: 'Casado(a) — Separação Total de Bens' },
  { value: 'CASADO_COMUNHAO_UNIVERSAL',    label: 'Casado(a) — Comunhão Universal de Bens' },
  { value: 'UNIAO_ESTAVEL',                label: 'União Estável' },
  { value: 'DIVORCIADO',                   label: 'Divorciado(a)' },
  { value: 'VIUVO',                        label: 'Viúvo(a)' },
  { value: 'PARTICIPACAO_FINAL_AQUESTOS',  label: 'Casado(a) — Participação Final nos Aquestos' },
  { value: 'OUTRO',                        label: 'Outro' },
]

const CANAL_ORIGEM_OPTIONS = ['Google', 'Instagram', 'Facebook', 'Indicação', 'Outro']

const SITUACAO_FORMACAO_OPTIONS = [
  'Graduação em Medicina',
  'Residência Médica',
  'Especialização',
  'Título de Especialista',
  'Pós-graduação / MBA',
  'Outro',
]

const TIPO_PIX_OPTIONS: { value: TipoPix; label: string }[] = [
  { value: 'CPF',       label: 'CPF' },
  { value: 'CNPJ',      label: 'CNPJ' },
  { value: 'EMAIL',     label: 'E-mail' },
  { value: 'TELEFONE',  label: 'Telefone' },
  { value: 'ALEATORIA', label: 'Chave Aleatória' },
]

function isCasadoOuUniao(estadoCivil: EstadoCivil | ''): boolean {
  return estadoCivil === 'UNIAO_ESTAVEL' || estadoCivil.startsWith('CASADO_') || estadoCivil === 'PARTICIPACAO_FINAL_AQUESTOS'
}

// ─── Form state ───────────────────────────────────────────────────────────────

interface FormState {
  nome: string
  cpf: string
  crm: string
  crmUf: string
  email: string
  telefone: string
  dataNascimento: string
  nacionalidade: string
  naturalidade: string
  estadoCivil: EstadoCivil | ''
  nomeMae: string
  nomePai: string
  logradouro: string
  numero: string
  complemento: string
  bairro: string
  cidade: string
  uf: string
  cep: string
  rgNumero: string
  rgOrgaoExpedidor: string
  rgUf: string
  rqe: string
  canalOrigem: string
  nomeIndicador: string
  situacaoFormacao: string[]
  areasAtuacao: string
  procedimentosRealiza: string
}

const emptyForm = (): FormState => ({
  nome: '', cpf: '', crm: '', crmUf: '', email: '', telefone: '',
  dataNascimento: '', nacionalidade: '', naturalidade: '', estadoCivil: '',
  nomeMae: '', nomePai: '',
  logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '', cep: '',
  rgNumero: '', rgOrgaoExpedidor: '', rgUf: '', rqe: '',
  canalOrigem: '', nomeIndicador: '',
  situacaoFormacao: [], areasAtuacao: '', procedimentosRealiza: '',
})

function fromResponse(r: CandidaturaPublicaResponse): FormState {
  return {
    nome: r.nome ?? '', cpf: formatCpf(r.cpf ?? ''), crm: r.crm ?? '', crmUf: r.crmUf ?? '',
    email: r.email ?? '', telefone: r.telefone ?? '',
    dataNascimento: r.dataNascimento ?? '', nacionalidade: r.nacionalidade ?? '',
    naturalidade: r.naturalidade ?? '', estadoCivil: r.estadoCivil ?? '',
    nomeMae: r.nomeMae ?? '', nomePai: r.nomePai ?? '',
    logradouro: r.logradouro ?? '', numero: r.numero ?? '', complemento: r.complemento ?? '',
    bairro: r.bairro ?? '', cidade: r.cidade ?? '', uf: r.uf ?? '', cep: r.cep ?? '',
    rgNumero: r.rgNumero ?? '', rgOrgaoExpedidor: r.rgOrgaoExpedidor ?? '', rgUf: r.rgUf ?? '',
    rqe: r.rqe ?? '', canalOrigem: r.canalOrigem ?? '', nomeIndicador: r.nomeIndicador ?? '',
    situacaoFormacao: r.situacaoFormacao ?? [], areasAtuacao: r.areasAtuacao ?? '',
    procedimentosRealiza: r.procedimentosRealiza ?? '',
  }
}

function toRequest(f: FormState): CandidaturaPublicaRequest {
  return {
    nome: f.nome.trim(),
    cpf: f.cpf.replace(/\D/g, ''),
    crm: f.crm.trim(),
    crmUf: f.crmUf,
    email: f.email.trim(),
    telefone: f.telefone.trim() || null,
    dataNascimento: f.dataNascimento || null,
    nacionalidade: f.nacionalidade.trim() || null,
    naturalidade: f.naturalidade.trim() || null,
    estadoCivil: f.estadoCivil || null,
    nomeMae: f.nomeMae.trim() || null,
    nomePai: f.nomePai.trim() || null,
    logradouro: f.logradouro.trim() || null,
    numero: f.numero.trim() || null,
    complemento: f.complemento.trim() || null,
    bairro: f.bairro.trim() || null,
    cidade: f.cidade.trim() || null,
    uf: f.uf || null,
    cep: f.cep.trim() || null,
    rgNumero: f.rgNumero.trim() || null,
    rgOrgaoExpedidor: f.rgOrgaoExpedidor.trim() || null,
    rgUf: f.rgUf || null,
    rqe: f.rqe.trim() || null,
    canalOrigem: f.canalOrigem || null,
    nomeIndicador: f.canalOrigem === 'Indicação' ? (f.nomeIndicador.trim() || null) : null,
    situacaoFormacao: f.situacaoFormacao.length > 0 ? f.situacaoFormacao : null,
    areasAtuacao: f.areasAtuacao.trim() || null,
    procedimentosRealiza: f.procedimentosRealiza.trim() || null,
  }
}

interface BankForm {
  tipoRecebimento: 'PIX' | 'TED'
  tipoPix: TipoPix | ''
  chavePix: string
  cpfsAdicionaisSplit: string
  bancoCodigo: string
  bancoNome: string
  agencia: string
  conta: string
  tipoConta: TipoConta | ''
}

const emptyBank = (): BankForm => ({
  tipoRecebimento: 'PIX',
  tipoPix: '', chavePix: '', cpfsAdicionaisSplit: '',
  bancoCodigo: '', bancoNome: '', agencia: '', conta: '', tipoConta: '',
})

interface LgpdForm {
  aceiteDeclaracaoVeracidade: boolean
  autorizacaoUsoDados: boolean
  autorizacaoCompartilhamento: boolean
  avisoPrivacidadeLido: boolean
  assinaturaNome: string
}

const emptyLgpd = (): LgpdForm => ({
  aceiteDeclaracaoVeracidade: false,
  autorizacaoUsoDados: false,
  autorizacaoCompartilhamento: false,
  avisoPrivacidadeLido: false,
  assinaturaNome: '',
})

// ─── Página ───────────────────────────────────────────────────────────────────

export function CadastroMedicoWizardPage() {
  const [step, setStep]             = useState(0)
  const [maxVisited, setMaxVisited] = useState(0)
  const [form, setForm]             = useState<FormState>(emptyForm)
  const [bank, setBank]             = useState<BankForm>(emptyBank)
  const [lgpd, setLgpd]             = useState<LgpdForm>(emptyLgpd)
  const [candidaturaId, setCandidaturaId] = useState<string | null>(null)
  const [errors, setErrors]         = useState<Partial<Record<string, string>>>({})
  const [apiError, setApiError]     = useState<string | null>(null)
  const [saving, setSaving]         = useState(false)
  const [restoring, setRestoring]   = useState(true)
  const [resumeNotice, setResumeNotice] = useState(false)
  const [enviado, setEnviado]       = useState(false)
  const [mensagemFinal, setMensagemFinal] = useState('')

  const [docsEnviados, setDocsEnviados] = useState<Partial<Record<TipoDocumentoCandidatura, UploadedFileRef[]>>>({})

  // ── Retomar candidatura salva (reload / voltou depois) ──
  useEffect(() => {
    const savedId = sessionStorage.getItem(STORAGE_KEY_ID)
    if (!savedId) { setRestoring(false); return }

    candidaturaMedicoApi.buscar(savedId)
      .then(resp => {
        setCandidaturaId(resp.id)
        setForm(fromResponse(resp))
        setMaxVisited(2)
        setResumeNotice(true)
        candidaturaMedicoApi.listarDocumentos(resp.id)
          .then(docs => {
            const grouped: Partial<Record<TipoDocumentoCandidatura, UploadedFileRef[]>> = {}
            docs.forEach(d => {
              const lista = grouped[d.tipo] ?? []
              lista.push({ id: d.id, nomeArquivo: d.nomeArquivo })
              grouped[d.tipo] = lista
            })
            setDocsEnviados(grouped)
          })
          .catch(() => { /* falha ao listar documentos não impede retomar a candidatura */ })
      })
      .catch(() => {
        sessionStorage.removeItem(STORAGE_KEY_ID)
      })
      .finally(() => setRestoring(false))
  }, [])

  function setField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm(f => ({ ...f, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function toggleSituacaoFormacao(opcao: string) {
    setForm(f => ({
      ...f,
      situacaoFormacao: f.situacaoFormacao.includes(opcao)
        ? f.situacaoFormacao.filter(o => o !== opcao)
        : [...f.situacaoFormacao, opcao],
    }))
  }

  function setBankField<K extends keyof BankForm>(key: K, value: BankForm[K]) {
    setBank(b => ({ ...b, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function setLgpdField<K extends keyof LgpdForm>(key: K, value: LgpdForm[K]) {
    setLgpd(l => ({ ...l, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function validateStep(s: number): boolean {
    const errs: Partial<Record<string, string>> = {}
    if (s === 0) {
      if (!form.nome.trim()) errs.nome = 'Obrigatório'
      if (!form.cpf || !isValidCpf(form.cpf)) errs.cpf = 'CPF inválido'
      if (!form.email.trim() || !/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'E-mail inválido'
      if (!form.crm.trim()) errs.crm = 'Obrigatório'
      if (!form.crmUf) errs.crmUf = 'Obrigatório'
      if (!form.nomeMae.trim()) errs.nomeMae = 'Obrigatório'
    }
    if (s === 3) {
      if (bank.tipoRecebimento === 'PIX') {
        if (!bank.tipoPix) errs.tipoPix = 'Obrigatório'
        if (!bank.chavePix.trim()) errs.chavePix = 'Obrigatório'
      } else {
        if (!bank.bancoNome.trim()) errs.bancoNome = 'Obrigatório'
        if (!bank.agencia.trim()) errs.agencia = 'Obrigatório'
        if (!bank.conta.trim()) errs.conta = 'Obrigatório'
        if (!bank.tipoConta) errs.tipoConta = 'Obrigatório'
      }
    }
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  function validateLgpd(): boolean {
    const errs: Partial<Record<string, string>> = {}
    if (!lgpd.aceiteDeclaracaoVeracidade) errs.aceiteDeclaracaoVeracidade = 'Obrigatório'
    if (!lgpd.autorizacaoUsoDados) errs.autorizacaoUsoDados = 'Obrigatório'
    if (!lgpd.autorizacaoCompartilhamento) errs.autorizacaoCompartilhamento = 'Obrigatório'
    if (!lgpd.avisoPrivacidadeLido) errs.avisoPrivacidadeLido = 'Obrigatório'
    if (!lgpd.assinaturaNome.trim()) errs.assinaturaNome = 'Obrigatório'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function persist(): Promise<CandidaturaPublicaResponse | null> {
    setApiError(null)
    setSaving(true)
    try {
      const req = toRequest(form)
      const resp = candidaturaId
        ? await candidaturaMedicoApi.atualizar(candidaturaId, req)
        : await candidaturaMedicoApi.criar(req)
      setCandidaturaId(resp.id)
      sessionStorage.setItem(STORAGE_KEY_ID, resp.id)
      return resp
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar candidatura')
      return null
    } finally {
      setSaving(false)
    }
  }

  async function persistBank(): Promise<boolean> {
    if (!candidaturaId) {
      setApiError('Conclua a etapa 1 antes de informar dados bancários.')
      return false
    }
    setApiError(null)
    setSaving(true)
    try {
      const req: CandidaturaDadosBancariosRequest = {
        tipoRecebimento: bank.tipoRecebimento,
        tipoPix: bank.tipoRecebimento === 'PIX' ? bank.tipoPix || null : null,
        chavePix: bank.tipoRecebimento === 'PIX' ? bank.chavePix.trim() || null : null,
        cpfsAdicionaisSplit: bank.tipoRecebimento === 'PIX' ? bank.cpfsAdicionaisSplit.trim() || null : null,
        bancoCodigo: bank.tipoRecebimento === 'TED' ? bank.bancoCodigo.trim() || null : null,
        bancoNome: bank.tipoRecebimento === 'TED' ? bank.bancoNome.trim() || null : null,
        agencia: bank.tipoRecebimento === 'TED' ? bank.agencia.trim() || null : null,
        conta: bank.tipoRecebimento === 'TED' ? bank.conta.trim() || null : null,
        tipoConta: bank.tipoRecebimento === 'TED' ? bank.tipoConta || null : null,
      }
      await candidaturaMedicoApi.atualizarDadosBancarios(candidaturaId, req)
      return true
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar dados bancários')
      return false
    } finally {
      setSaving(false)
    }
  }

  function goTo(target: number) {
    if (target > maxVisited) return
    setErrors({})
    setApiError(null)
    setStep(target)
  }

  async function handleNext() {
    if (step === 3) {
      if (!validateStep(step)) return
      const ok = await persistBank()
      if (!ok) return
    } else {
      if (!validateStep(step)) return
      const saved = await persist()
      if (!saved) return
    }
    const next = step + 1
    if (next > maxVisited) setMaxVisited(next)
    setStep(next)
  }

  async function handleEnviar() {
    if (!validateLgpd()) return
    if (!candidaturaId) {
      setApiError('Conclua as etapas anteriores antes de enviar.')
      return
    }
    setApiError(null)
    setSaving(true)
    try {
      const req: DeclaracaoLgpdRequest = { ...lgpd }
      await candidaturaMedicoApi.registrarDeclaracaoLgpd(candidaturaId, req)
      const fin = await candidaturaMedicoApi.finalizar(candidaturaId)
      setMensagemFinal(fin.mensagem)
      setEnviado(true)
      sessionStorage.removeItem(STORAGE_KEY_ID)
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao enviar candidatura')
    } finally {
      setSaving(false)
    }
  }

  function handleDocUpload(tipo: TipoDocumentoCandidatura, file: File) {
    if (!candidaturaId) {
      setApiError('Conclua a etapa 1 antes de enviar documentos.')
      return Promise.resolve()
    }
    setApiError(null)
    return candidaturaMedicoApi.uploadDocumento(candidaturaId, tipo, file)
      .then(doc => setDocsEnviados(d => ({
        ...d,
        [tipo]: [...(d[tipo] ?? []), { id: doc.id, nomeArquivo: doc.nomeArquivo }],
      })))
      .catch(err => setApiError(err instanceof Error ? err.message : 'Erro ao enviar arquivo'))
  }

  function handleDocRemove(tipo: TipoDocumentoCandidatura, arquivo: UploadedFileRef) {
    if (!candidaturaId) return Promise.resolve()
    setApiError(null)
    return candidaturaMedicoApi.deletarDocumento(candidaturaId, arquivo.id)
      .then(() => setDocsEnviados(d => ({
        ...d,
        [tipo]: (d[tipo] ?? []).filter(a => a.id !== arquivo.id),
      })))
      .catch(err => setApiError(err instanceof Error ? err.message : 'Erro ao remover arquivo'))
  }

  if (restoring) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white">
        <Loader2 className="animate-spin text-primary" size={32} />
      </div>
    )
  }

  return (
    <div className="min-h-screen flex">
      {/* ── Painel esquerdo — branding ─────────────────────────────────── */}
      <div className="hidden lg:flex lg:w-1/2 bg-primary-700 items-center justify-center p-12">
        <div className="space-y-8 max-w-sm">
          <img src="/logo-pinsaude.png" alt="Pin Saúde" className="h-16 w-auto brightness-0 invert" />
          <div className="space-y-3">
            <h1 className="text-4xl font-black text-white leading-tight">
              Faça parte da<br />nossa rede médica
            </h1>
            <p className="text-primary-200 text-lg leading-relaxed">
              Preencha seus dados e documentos para iniciar seu credenciamento junto à Pin Saúde.
              Você pode salvar seu progresso e continuar depois.
            </p>
          </div>
        </div>
      </div>

      {/* ── Painel direito — wizard ────────────────────────────────────── */}
      <div className="flex-1 flex flex-col items-center bg-white px-6 py-10 overflow-y-auto">
        <div className="lg:hidden mb-6">
          <img src="/logo-pinsaude.png" alt="Pin Saúde" className="h-10 w-auto" />
        </div>

        <div className="w-full max-w-2xl">
          {!enviado && (
            <>
              <div className="mb-6 text-center">
                <h2 className="text-2xl font-bold text-gray-900">Credenciamento de Médico</h2>
                <p className="text-sm text-gray-500 mt-1">Boas-vindas! Vamos começar seu cadastro.</p>
              </div>

              <StepWizard steps={STEPS} current={step} maxVisited={maxVisited} onStepClick={goTo} className="mb-6" />

              {resumeNotice && (
                <Alert variant="info" onClose={() => setResumeNotice(false)} className="mb-4">
                  Encontramos uma candidatura salva — seus dados foram restaurados. Continue de onde parou.
                </Alert>
              )}

              {apiError && (
                <Alert variant="error" onClose={() => setApiError(null)} className="mb-4">
                  {apiError}
                </Alert>
              )}

              <div className="min-h-[300px]">
                {step === 0 && (
                  <StepDadosPessoais form={form} errors={errors} onChange={setField} />
                )}
                {step === 1 && (
                  <StepContatoEndereco
                    form={form}
                    onChange={setField}
                    docsEnviados={docsEnviados}
                    onUpload={handleDocUpload}
                    onRemove={handleDocRemove}
                  />
                )}
                {step === 2 && (
                  <StepDocumentosProfissionais
                    form={form}
                    errors={errors}
                    onChange={setField}
                    docsEnviados={docsEnviados}
                    onUpload={handleDocUpload}
                    onRemove={handleDocRemove}
                  />
                )}
                {step === 3 && (
                  <StepDadosBancarios
                    bank={bank}
                    errors={errors}
                    onChange={setBankField}
                    onBancoSelect={(nome, compe) => {
                      setBank(b => ({ ...b, bancoNome: nome, bancoCodigo: compe }))
                      setErrors(e => ({ ...e, bancoNome: undefined }))
                    }}
                  />
                )}
                {step === 4 && (
                  <StepFormacao
                    form={form}
                    onChange={setField}
                    onToggleSituacao={toggleSituacaoFormacao}
                    docsEnviados={docsEnviados}
                    onUpload={handleDocUpload}
                    onRemove={handleDocRemove}
                  />
                )}
                {step === 5 && (
                  <StepLgpd lgpd={lgpd} errors={errors} onChange={setLgpdField} />
                )}
              </div>

              <div className="flex justify-between pt-5 mt-2 border-t border-gray-200">
                <Button
                  type="button"
                  variant="outline"
                  onClick={step === 0 ? undefined : () => goTo(step - 1)}
                  disabled={step === 0}
                >
                  ← Voltar
                </Button>
                {step < LAST_STEP ? (
                  <Button type="button" onClick={handleNext} loading={saving}>
                    Próximo →
                  </Button>
                ) : (
                  <Button type="button" onClick={handleEnviar} loading={saving}>
                    Enviar candidatura ✓
                  </Button>
                )}
              </div>
            </>
          )}

          {enviado && (
            <div className="flex flex-col items-center text-center gap-4 py-10">
              <div className="w-16 h-16 rounded-full bg-secondary-100 flex items-center justify-center">
                <CheckCircle2 className="text-secondary-600" size={32} />
              </div>
              <h2 className="text-2xl font-bold text-gray-900">Candidatura enviada com sucesso!</h2>
              <p className="text-sm text-gray-500 max-w-md">
                {mensagemFinal || 'Você receberá um e-mail assim que a análise for concluída.'}
              </p>
              <p className="text-xs text-gray-400 max-w-md">
                Nossa equipe vai revisar seus dados e documentos. Assim que sua candidatura for
                aprovada, você poderá acessar o sistema com o e-mail informado.
              </p>
            </div>
          )}

          <p className="text-center text-sm text-gray-500 mt-8">
            Já é cadastrado?{' '}
            <Link to="/login" className="text-primary hover:text-primary-700 transition-colors font-medium">
              Fazer login
            </Link>
          </p>

          <p className="text-center text-xs text-gray-400 mt-2">
            © {new Date().getFullYear()} Pin Saúde · Todos os direitos reservados
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── Step 0: Dados Pessoais ───────────────────────────────────────────────────

function StepDadosPessoais({
  form,
  errors,
  onChange,
}: {
  form: FormState
  errors: Partial<Record<string, string>>
  onChange: <K extends keyof FormState>(k: K, v: FormState[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Informe seus dados pessoais e de identificação profissional.</p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <Input
            label="Nome completo *"
            value={form.nome}
            onChange={e => onChange('nome', e.target.value)}
            error={errors.nome}
            placeholder="Seu nome completo"
          />
        </div>
        <CpfInput
          label="CPF *"
          value={form.cpf}
          onChange={v => onChange('cpf', v)}
          error={errors.cpf}
        />
        <Input
          label="E-mail principal *"
          type="email"
          value={form.email}
          onChange={e => onChange('email', e.target.value)}
          error={errors.email}
          placeholder="seu@email.com.br"
          hint="Este será o seu login de acesso ao sistema"
        />
        <PhoneInput
          label="Telefone / WhatsApp"
          value={form.telefone}
          onChange={v => onChange('telefone', v)}
        />
        <Input
          label="Data de nascimento"
          type="date"
          value={form.dataNascimento}
          onChange={e => onChange('dataNascimento', e.target.value)}
        />
        <Input
          label="CRM *"
          value={form.crm}
          onChange={e => onChange('crm', e.target.value)}
          error={errors.crm}
          placeholder="Ex: 123456"
        />
        <SelectField
          label="UF do CRM *"
          value={form.crmUf}
          onChange={v => onChange('crmUf', v)}
          error={errors.crmUf}
        >
          {UFS.map(uf => <option key={uf} value={uf}>{uf}</option>)}
        </SelectField>
        <Input
          label="Nacionalidade"
          value={form.nacionalidade}
          onChange={e => onChange('nacionalidade', e.target.value)}
          placeholder="Ex: Brasileira"
        />
        <Input
          label="Naturalidade"
          value={form.naturalidade}
          onChange={e => onChange('naturalidade', e.target.value)}
          placeholder="Cidade onde nasceu"
        />
        <SelectField
          label="Estado civil"
          value={form.estadoCivil}
          onChange={v => onChange('estadoCivil', v as EstadoCivil | '')}
        >
          {ESTADO_CIVIL_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </SelectField>
        <Input
          label="Nome da mãe *"
          value={form.nomeMae}
          onChange={e => onChange('nomeMae', e.target.value)}
          error={errors.nomeMae}
        />
        <Input
          label="Nome do pai"
          value={form.nomePai}
          onChange={e => onChange('nomePai', e.target.value)}
        />
      </div>
    </div>
  )
}

// ─── Step 1: Contato e Endereço ───────────────────────────────────────────────

function StepContatoEndereco({
  form,
  onChange,
  docsEnviados,
  onUpload,
  onRemove,
}: {
  form: FormState
  onChange: <K extends keyof FormState>(k: K, v: FormState[K]) => void
  docsEnviados: Partial<Record<TipoDocumentoCandidatura, UploadedFileRef[]>>
  onUpload: (tipo: TipoDocumentoCandidatura, file: File) => Promise<unknown>
  onRemove: (tipo: TipoDocumentoCandidatura, arquivo: UploadedFileRef) => Promise<unknown>
}) {
  const mostrarCertidao = isCasadoOuUniao(form.estadoCivil)

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Informe seu endereço e como podemos te encontrar.</p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <Input
            label="Logradouro"
            value={form.logradouro}
            onChange={e => onChange('logradouro', e.target.value)}
            placeholder="Rua, avenida..."
          />
        </div>
        <Input
          label="Número"
          value={form.numero}
          onChange={e => onChange('numero', e.target.value)}
        />
        <Input
          label="Complemento"
          value={form.complemento}
          onChange={e => onChange('complemento', e.target.value)}
        />
        <Input
          label="Bairro"
          value={form.bairro}
          onChange={e => onChange('bairro', e.target.value)}
        />
        <Input
          label="Cidade"
          value={form.cidade}
          onChange={e => onChange('cidade', e.target.value)}
        />
        <SelectField
          label="UF"
          value={form.uf}
          onChange={v => onChange('uf', v)}
        >
          {UFS.map(uf => <option key={uf} value={uf}>{uf}</option>)}
        </SelectField>
        <Input
          label="CEP"
          value={form.cep}
          onChange={e => onChange('cep', e.target.value)}
          placeholder="00000-000"
        />
        <Input
          label="RG"
          value={form.rgNumero}
          onChange={e => onChange('rgNumero', e.target.value)}
        />
        <Input
          label="Órgão expedidor"
          value={form.rgOrgaoExpedidor}
          onChange={e => onChange('rgOrgaoExpedidor', e.target.value)}
          placeholder="Ex: SSP"
        />
        <SelectField
          label="UF do RG"
          value={form.rgUf}
          onChange={v => onChange('rgUf', v)}
        >
          {UFS.map(uf => <option key={uf} value={uf}>{uf}</option>)}
        </SelectField>
        <SelectField
          label="Como você conheceu a Pin Saúde?"
          value={form.canalOrigem}
          onChange={v => onChange('canalOrigem', v)}
        >
          {CANAL_ORIGEM_OPTIONS.map(o => <option key={o} value={o}>{o}</option>)}
        </SelectField>
        {form.canalOrigem === 'Indicação' && (
          <Input
            label="Nome de quem indicou"
            value={form.nomeIndicador}
            onChange={e => onChange('nomeIndicador', e.target.value)}
          />
        )}
      </div>

      <div className="flex flex-col gap-3 pt-2">
        <MultiFileUploadField
          label="Comprovante de endereço"
          tipo="COMPROVANTE_ENDERECO"
          arquivos={docsEnviados.COMPROVANTE_ENDERECO ?? []}
          onUpload={onUpload}
          onRemove={onRemove}
          multiplos={false}
        />
        {mostrarCertidao && (
          <MultiFileUploadField
            label="Certidão de casamento / união estável"
            tipo="CERTIDAO_CASAMENTO"
            arquivos={docsEnviados.CERTIDAO_CASAMENTO ?? []}
            onUpload={onUpload}
            onRemove={onRemove}
            multiplos={false}
          />
        )}
      </div>
    </div>
  )
}

// ─── Step 2: Documentos Profissionais ─────────────────────────────────────────

function StepDocumentosProfissionais({
  form,
  errors,
  onChange,
  docsEnviados,
  onUpload,
  onRemove,
}: {
  form: FormState
  errors: Partial<Record<string, string>>
  onChange: <K extends keyof FormState>(k: K, v: FormState[K]) => void
  docsEnviados: Partial<Record<TipoDocumentoCandidatura, UploadedFileRef[]>>
  onUpload: (tipo: TipoDocumentoCandidatura, file: File) => Promise<unknown>
  onRemove: (tipo: TipoDocumentoCandidatura, arquivo: UploadedFileRef) => Promise<unknown>
}) {
  const crmEnviado = (docsEnviados.CRM ?? []).length > 0

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">
        Envie os documentos que comprovam seu registro e, se aplicável, sua(s) especialidade(s).
      </p>
      <Input
        label="RQE (opcional)"
        value={form.rqe}
        onChange={e => onChange('rqe', e.target.value)}
        error={errors.rqe}
        placeholder="Registro de Qualificação de Especialista"
      />

      <div className="flex flex-col gap-3 pt-2">
        <MultiFileUploadField
          label="Foto ou digitalização do CRM *"
          tipo="CRM"
          arquivos={docsEnviados.CRM ?? []}
          onUpload={onUpload}
          onRemove={onRemove}
          multiplos={false}
        />
        {form.rqe.trim() !== '' && (
          <MultiFileUploadField
            label="Documento do RQE"
            tipo="RQE"
            arquivos={docsEnviados.RQE ?? []}
            onUpload={onUpload}
            onRemove={onRemove}
            multiplos={false}
          />
        )}
      </div>

      {!crmEnviado && (
        <Alert variant="warning">
          O documento do CRM é obrigatório para a conclusão da sua candidatura.
        </Alert>
      )}
    </div>
  )
}

// ─── Step 3: Dados Bancários ──────────────────────────────────────────────────

function StepDadosBancarios({
  bank,
  errors,
  onChange,
  onBancoSelect,
}: {
  bank: BankForm
  errors: Partial<Record<string, string>>
  onChange: <K extends keyof BankForm>(k: K, v: BankForm[K]) => void
  onBancoSelect: (nome: string, compe: string) => void
}) {
  const isTed = bank.tipoRecebimento === 'TED'
  const bancoSelecionado = bancos.find(b => b.compe === bank.bancoCodigo) ?? null

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">
        Informe como você prefere receber seus repasses. Esses dados podem ser alterados depois.
      </p>

      <div className="flex rounded-lg border border-gray-200 overflow-hidden self-start">
        {(['PIX', 'TED'] as const).map(tipo => (
          <button
            key={tipo}
            type="button"
            onClick={() => onChange('tipoRecebimento', tipo)}
            className={[
              'px-6 py-2 text-sm font-medium transition-colors',
              bank.tipoRecebimento === tipo
                ? 'bg-primary text-white'
                : 'bg-white text-gray-600 hover:bg-gray-50',
            ].join(' ')}
          >
            {tipo}
          </button>
        ))}
      </div>

      {!isTed ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <SelectField
            label="Tipo de chave PIX *"
            value={bank.tipoPix}
            onChange={v => onChange('tipoPix', v as TipoPix | '')}
            error={errors.tipoPix}
          >
            {TIPO_PIX_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </SelectField>
          <Input
            label="Chave PIX *"
            value={bank.chavePix}
            onChange={e => onChange('chavePix', e.target.value)}
            error={errors.chavePix}
            disabled={!bank.tipoPix}
          />
          <div className="sm:col-span-2">
            <Input
              label="CPFs para split acima de R$ 40.000 (separados por vírgula)"
              value={bank.cpfsAdicionaisSplit}
              onChange={e => onChange('cpfsAdicionaisSplit', e.target.value)}
              placeholder="Ex: 000.000.000-00, 111.111.111-11"
            />
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <BancoSelect
              value={bank.bancoNome}
              onChange={onBancoSelect}
              error={errors.bancoNome}
            />
          </div>
          <Input
            label="Agência *"
            value={bank.agencia}
            onChange={e => onChange('agencia', e.target.value)}
            error={errors.agencia}
            placeholder="Ex: 1234"
          />
          <Input
            label="Conta *"
            value={bank.conta}
            onChange={e => onChange('conta', e.target.value)}
            error={errors.conta}
            placeholder="Ex: 12345-6"
          />
          <div className="sm:col-span-2 flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700">Tipo de conta *</label>
            <div className="flex gap-4">
              {(['CORRENTE', 'POUPANCA'] as TipoConta[]).map(tipo => (
                <label key={tipo} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="tipoConta"
                    value={tipo}
                    checked={bank.tipoConta === tipo}
                    onChange={() => onChange('tipoConta', tipo)}
                    className="accent-primary"
                  />
                  <span className="text-sm text-gray-700">
                    {tipo === 'CORRENTE' ? 'Corrente' : 'Poupança'}
                  </span>
                </label>
              ))}
            </div>
            {errors.tipoConta && <p className="text-xs text-red-500">{errors.tipoConta}</p>}
          </div>
          {bank.bancoCodigo && bank.agencia && bank.conta && (
            <div className="sm:col-span-2 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
              <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Resumo TED</p>
              <div className="mt-1 flex items-center gap-2">
                {bancoSelecionado && <BancoAvatar banco={bancoSelecionado} size={20} />}
                <p className="text-sm font-semibold text-gray-900">
                  {bank.bancoNome || `Banco ${bank.bancoCodigo}`} · Ag. {bank.agencia} · Cc. {bank.conta}
                </p>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Step 4: Formação e Currículo ─────────────────────────────────────────────

function StepFormacao({
  form,
  onChange,
  onToggleSituacao,
  docsEnviados,
  onUpload,
  onRemove,
}: {
  form: FormState
  onChange: <K extends keyof FormState>(k: K, v: FormState[K]) => void
  onToggleSituacao: (opcao: string) => void
  docsEnviados: Partial<Record<TipoDocumentoCandidatura, UploadedFileRef[]>>
  onUpload: (tipo: TipoDocumentoCandidatura, file: File) => Promise<unknown>
  onRemove: (tipo: TipoDocumentoCandidatura, arquivo: UploadedFileRef) => Promise<unknown>
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Conte um pouco sobre sua formação e área de atuação.</p>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">Situação de formação</label>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {SITUACAO_FORMACAO_OPTIONS.map(opcao => (
            <label key={opcao} className="flex items-center gap-2 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={form.situacaoFormacao.includes(opcao)}
                onChange={() => onToggleSituacao(opcao)}
                className="w-4 h-4 accent-primary rounded"
              />
              <span className="text-sm text-gray-700">{opcao}</span>
            </label>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">Áreas de atuação</label>
        <textarea
          value={form.areasAtuacao}
          onChange={e => onChange('areasAtuacao', e.target.value)}
          rows={3}
          placeholder="Ex: Clínica geral, cardiologia, plantões de urgência..."
          className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary resize-none"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">Procedimentos que realiza</label>
        <textarea
          value={form.procedimentosRealiza}
          onChange={e => onChange('procedimentosRealiza', e.target.value)}
          rows={3}
          placeholder="Descreva os principais procedimentos que você realiza"
          className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary resize-none"
        />
      </div>

      <div className="flex flex-col gap-3 pt-2">
        <MultiFileUploadField
          label="Certificado de residência / especialização (opcional)"
          tipo="RESIDENCIA"
          arquivos={docsEnviados.RESIDENCIA ?? []}
          onUpload={onUpload}
          onRemove={onRemove}
          multiplos
        />
        <MultiFileUploadField
          label="Títulos de especialista (envie quantos tiver)"
          tipo="ESPECIALIDADES"
          arquivos={docsEnviados.ESPECIALIDADES ?? []}
          onUpload={onUpload}
          onRemove={onRemove}
          multiplos
        />
      </div>
    </div>
  )
}

// ─── Step 5: LGPD e Assinatura Eletrônica ─────────────────────────────────────

function StepLgpd({
  lgpd,
  errors,
  onChange,
}: {
  lgpd: LgpdForm
  errors: Partial<Record<string, string>>
  onChange: <K extends keyof LgpdForm>(k: K, v: LgpdForm[K]) => void
}) {
  const checks: { key: keyof LgpdForm; label: string }[] = [
    { key: 'aceiteDeclaracaoVeracidade', label: 'Declaro que todas as informações prestadas neste formulário são verdadeiras.' },
    { key: 'autorizacaoUsoDados', label: 'Autorizo o uso dos meus dados pessoais para fins de contratação e credenciamento junto à Pin Saúde.' },
    { key: 'autorizacaoCompartilhamento', label: 'Autorizo o compartilhamento dos meus dados com terceiros quando necessário para o credenciamento.' },
    { key: 'avisoPrivacidadeLido', label: 'Li e concordo com o Aviso de Privacidade (LGPD) da Pin Saúde.' },
  ]

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">
        Para finalizar, confirme as declarações abaixo e assine eletronicamente.
      </p>

      <div className="flex flex-col gap-3">
        {checks.map(({ key, label }) => (
          <label key={key} className="flex items-start gap-2.5 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={lgpd[key] as boolean}
              onChange={e => onChange(key, e.target.checked as LgpdForm[typeof key])}
              className="w-4 h-4 mt-0.5 accent-primary rounded shrink-0"
            />
            <span className="text-sm text-gray-700">{label}</span>
          </label>
        ))}
        {(errors.aceiteDeclaracaoVeracidade || errors.autorizacaoUsoDados
          || errors.autorizacaoCompartilhamento || errors.avisoPrivacidadeLido) && (
          <p className="text-xs text-red-500">Todas as declarações acima são obrigatórias.</p>
        )}
      </div>

      <Input
        label="Assinatura eletrônica — digite seu nome completo *"
        value={lgpd.assinaturaNome}
        onChange={e => onChange('assinaturaNome', e.target.value)}
        error={errors.assinaturaNome}
        placeholder="Seu nome completo"
        hint="Ao digitar seu nome, você confirma eletronicamente as declarações acima"
      />
    </div>
  )
}

// ─── Select compartilhado ─────────────────────────────────────────────────────

function SelectField({
  label,
  value,
  onChange,
  error,
  children,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-sm font-medium text-gray-700">{label}</label>
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        className={[
          'block w-full rounded-lg border px-3 py-2 text-sm',
          'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary',
          error ? 'border-red-400' : 'border-gray-300',
        ].join(' ')}
      >
        <option value="">Selecione...</option>
        {children}
      </select>
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  )
}
