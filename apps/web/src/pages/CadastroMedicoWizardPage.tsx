import { useEffect, useRef, useState } from 'react'
import {
  User, Home, FileText, ShieldCheck, HeartHandshake, Stethoscope,
  Upload, CheckCircle2, Loader2,
} from 'lucide-react'
import { Input, Button, Alert, StepWizard } from '@pinsaude/ui'
import { CpfInput } from '../components/CpfInput'
import {
  CandidaturaPublicaRequest, CandidaturaPublicaResponse, EstadoCivil,
  TipoDocumentoCandidatura, candidaturaMedicoApi,
} from '../api/candidaturaMedicoApi'
import { isValidCpf, formatCpf } from '../utils/cpf'

// ─── Constantes ───────────────────────────────────────────────────────────────

const STORAGE_KEY_ID = 'pinsaude_candidatura_id'

// Todos os 6 passos da jornada completa (EPIC-14.6 implementa 1-3; 14.7 implementa 4-6
// no mesmo arquivo). Mostrar os 6 dá ao médico o contexto de quanto falta, mesmo que os
// últimos 3 ainda não sejam navegáveis (maxVisited nunca ultrapassa o índice 2 aqui).
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
}

const emptyForm = (): FormState => ({
  nome: '', cpf: '', crm: '', crmUf: '', email: '', telefone: '',
  dataNascimento: '', nacionalidade: '', naturalidade: '', estadoCivil: '',
  nomeMae: '', nomePai: '',
  logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '', cep: '',
  rgNumero: '', rgOrgaoExpedidor: '', rgUf: '', rqe: '',
  canalOrigem: '', nomeIndicador: '',
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
    situacaoFormacao: null,
    areasAtuacao: null,
    procedimentosRealiza: null,
  }
}

// ─── Página ───────────────────────────────────────────────────────────────────

export function CadastroMedicoWizardPage() {
  const [step, setStep]             = useState(0)
  const [maxVisited, setMaxVisited] = useState(0)
  const [form, setForm]             = useState<FormState>(emptyForm)
  const [candidaturaId, setCandidaturaId] = useState<string | null>(null)
  const [errors, setErrors]     = useState<Partial<Record<keyof FormState, string>>>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [saving, setSaving]     = useState(false)
  const [restoring, setRestoring] = useState(true)
  const [resumeNotice, setResumeNotice] = useState(false)
  const [concluido, setConcluido] = useState(false)

  const [docsEnviados, setDocsEnviados] = useState<Partial<Record<TipoDocumentoCandidatura, string>>>({})

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

  function validateStep(s: number): boolean {
    const errs: Partial<Record<keyof FormState, string>> = {}
    if (s === 0) {
      if (!form.nome.trim()) errs.nome = 'Obrigatório'
      if (!form.cpf || !isValidCpf(form.cpf)) errs.cpf = 'CPF inválido'
      if (!form.email.trim() || !/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'E-mail inválido'
      if (!form.crm.trim()) errs.crm = 'Obrigatório'
      if (!form.crmUf) errs.crmUf = 'Obrigatório'
      if (!form.nomeMae.trim()) errs.nomeMae = 'Obrigatório'
    }
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

  function goTo(target: number) {
    if (target > maxVisited) return
    setErrors({})
    setApiError(null)
    setStep(target)
  }

  async function handleNext() {
    if (!validateStep(step)) return
    const saved = await persist()
    if (!saved) return
    const next = step + 1
    if (next > maxVisited) setMaxVisited(next)
    setStep(next)
  }

  async function handleConcluirEtapa3() {
    const saved = await persist()
    if (!saved) return
    setConcluido(true)
  }

  function handleDocUpload(tipo: TipoDocumentoCandidatura, file: File) {
    if (!candidaturaId) {
      setApiError('Conclua a etapa 1 antes de enviar documentos.')
      return
    }
    setApiError(null)
    candidaturaMedicoApi.uploadDocumento(candidaturaId, tipo, file)
      .then(doc => setDocsEnviados(d => ({ ...d, [tipo]: doc.nomeArquivo })))
      .catch(err => setApiError(err instanceof Error ? err.message : 'Erro ao enviar arquivo'))
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
          {!concluido && (
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
                  />
                )}
                {step === 2 && (
                  <StepDocumentosProfissionais
                    form={form}
                    errors={errors}
                    onChange={setField}
                    docsEnviados={docsEnviados}
                    onUpload={handleDocUpload}
                  />
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
                {step < 2 ? (
                  <Button type="button" onClick={handleNext} loading={saving}>
                    Próximo →
                  </Button>
                ) : (
                  <Button type="button" onClick={handleConcluirEtapa3} loading={saving}>
                    Concluir por enquanto
                  </Button>
                )}
              </div>
            </>
          )}

          {concluido && (
            <div className="flex flex-col items-center text-center gap-4 py-10">
              <div className="w-16 h-16 rounded-full bg-secondary-100 flex items-center justify-center">
                <CheckCircle2 className="text-secondary-600" size={32} />
              </div>
              <h2 className="text-2xl font-bold text-gray-900">Recebemos seus dados até aqui!</h2>
              <p className="text-sm text-gray-500 max-w-md">
                Suas informações pessoais, de contato e documentos profissionais foram salvos com sucesso.
                Em breve disponibilizaremos as próximas etapas (dados bancários, formação e declarações LGPD)
                para você concluir sua candidatura. Você pode fechar esta página — seu progresso está salvo.
              </p>
              <Button type="button" variant="outline" onClick={() => setConcluido(false)}>
                ← Revisar meus dados
              </Button>
            </div>
          )}

          <p className="text-center text-xs text-gray-400 mt-8">
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
  errors: Partial<Record<keyof FormState, string>>
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
        <Input
          label="Telefone / WhatsApp"
          value={form.telefone}
          onChange={e => onChange('telefone', e.target.value)}
          placeholder="(00) 00000-0000"
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
}: {
  form: FormState
  onChange: <K extends keyof FormState>(k: K, v: FormState[K]) => void
  docsEnviados: Partial<Record<TipoDocumentoCandidatura, string>>
  onUpload: (tipo: TipoDocumentoCandidatura, file: File) => void
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
        <UploadField
          label="Comprovante de endereço"
          tipo="COMPROVANTE_ENDERECO"
          arquivoEnviado={docsEnviados.COMPROVANTE_ENDERECO}
          onUpload={onUpload}
        />
        {mostrarCertidao && (
          <UploadField
            label="Certidão de casamento / união estável"
            tipo="CERTIDAO_CASAMENTO"
            arquivoEnviado={docsEnviados.CERTIDAO_CASAMENTO}
            onUpload={onUpload}
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
}: {
  form: FormState
  errors: Partial<Record<keyof FormState, string>>
  onChange: <K extends keyof FormState>(k: K, v: FormState[K]) => void
  docsEnviados: Partial<Record<TipoDocumentoCandidatura, string>>
  onUpload: (tipo: TipoDocumentoCandidatura, file: File) => void
}) {
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
        <UploadField
          label="Foto ou digitalização do CRM *"
          tipo="CRM"
          arquivoEnviado={docsEnviados.CRM}
          onUpload={onUpload}
        />
        {form.rqe.trim() !== '' && (
          <UploadField
            label="Documento do RQE"
            tipo="RQE"
            arquivoEnviado={docsEnviados.RQE}
            onUpload={onUpload}
          />
        )}
      </div>

      {!docsEnviados.CRM && (
        <Alert variant="warning">
          O documento do CRM é obrigatório para a conclusão da sua candidatura.
        </Alert>
      )}
    </div>
  )
}

// ─── Upload field (simplificado — extração completa do padrão de drag-and-drop
// multi-arquivo fica para a 14.7, junto com os componentes de upload das etapas
// 4-6) ──────────────────────────────────────────────────────────────────────

function UploadField({
  label,
  tipo,
  arquivoEnviado,
  onUpload,
}: {
  label: string
  tipo: TipoDocumentoCandidatura
  arquivoEnviado?: string
  onUpload: (tipo: TipoDocumentoCandidatura, file: File) => void
}) {
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  function handleFile(file: File | undefined) {
    if (!file) return
    setUploading(true)
    Promise.resolve(onUpload(tipo, file)).finally(() => setUploading(false))
  }

  return (
    <div
      onDragOver={e => { e.preventDefault(); setDragging(true) }}
      onDragLeave={() => setDragging(false)}
      onDrop={e => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]) }}
      onClick={() => inputRef.current?.click()}
      className={[
        'flex items-center gap-3 px-4 py-3 rounded-xl border-2 border-dashed cursor-pointer transition-colors',
        dragging ? 'border-primary bg-primary-50' : 'border-gray-200 hover:border-primary hover:bg-primary-50',
      ].join(' ')}
    >
      {arquivoEnviado ? (
        <CheckCircle2 className="text-secondary-600 shrink-0" size={18} />
      ) : (
        <Upload className={['shrink-0', dragging ? 'text-primary' : 'text-gray-400'].join(' ')} size={18} />
      )}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-700">{label}</p>
        <p className="text-xs text-gray-400 truncate">
          {uploading ? 'Enviando...' : arquivoEnviado ? `Enviado: ${arquivoEnviado}` : 'Clique ou arraste o arquivo aqui (PDF, JPG, PNG)'}
        </p>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.jpg,.jpeg,.png"
        className="hidden"
        onChange={e => { handleFile(e.target.files?.[0]); e.target.value = '' }}
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
