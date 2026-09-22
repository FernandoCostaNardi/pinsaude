import {
  LayoutDashboard,
  Stethoscope,
  Building2,
  Hospital,
  ClipboardList,
  FileText,
  Banknote,
  ArrowLeftRight,
  BarChart3,
  Users,
  BookOpen,
  ClipboardCheck,
  SlidersHorizontal,
  Layers,
  HeartPulse,
  TrendingUp,
  PlusCircle,
  Upload,
  Wallet,
  CalendarDays,
  PackageCheck,
  type LucideIcon,
} from 'lucide-react'

export const BACKOFFICE = ['operacao', 'gestao', 'financeiro', 'contabil']

/** Agrupamento do checklist de permissões no formulário de perfil customizado (PERFIL-07). */
export type Area = 'Cadastros' | 'Faturamento' | 'Fiscal' | 'Financeiro' | 'Gestão'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  roles: string[]
  end: boolean
  /**
   * Chave perm_* do catálogo fechado (PERFIL-01/ADR-004). Ausente para telas fora do
   * catálogo por design — Dashboard (agrega dados, sem operação sensível própria) e o
   * Portal do Médico (role `medico` fixa, não é um perfil customizável por este projeto).
   */
  perm?: string
  /** Área do checklist (PERFIL-07) — presente sempre que `perm` também estiver. */
  area?: Area
}

export const navItems: NavItem[] = [
  // ── Portal do Médico ─────────────────────────────────────────────────────────
  { to: '/portal/dashboard',     label: 'Meu Portal',        icon: HeartPulse,       roles: ['medico'], end: true  },
  { to: '/portal/extrato',       label: 'Extrato',           icon: TrendingUp,       roles: ['medico'], end: true  },
  { to: '/portal/producao/nova', label: 'Informar Produção', icon: PlusCircle,       roles: ['medico'], end: true  },
  { to: '/portal/frequencias',   label: 'Frequências',       icon: CalendarDays,     roles: ['medico'], end: true  },
  // ── Backoffice ───────────────────────────────────────────────────────────────
  { to: '/',                       label: 'Dashboard',        icon: LayoutDashboard,  roles: BACKOFFICE,                        end: true  },
  { to: '/medicos',                label: 'Médicos',          icon: Stethoscope,      roles: ['gestao', 'operacao'],            end: false, perm: 'perm_medicos',      area: 'Cadastros' },
  { to: '/medicos/aprovacao',      label: 'Aprovação',        icon: ClipboardCheck,   roles: ['gestao', 'operacao'],            end: true,  perm: 'perm_medicos',      area: 'Cadastros' },
  { to: '/empresas',               label: 'Empresas',         icon: Building2,        roles: ['gestao'],                        end: true,  perm: 'perm_empresas',     area: 'Cadastros' },
  { to: '/tomadores',              label: 'Tomadores',        icon: Hospital,         roles: BACKOFFICE,                        end: true,  perm: 'perm_tomadores',    area: 'Cadastros' },
  { to: '/producao',               label: 'Produção',         icon: ClipboardList,    roles: BACKOFFICE,                        end: false, perm: 'perm_producao',     area: 'Faturamento' },
  { to: '/frequencias',            label: 'Frequências',      icon: CalendarDays,     roles: BACKOFFICE,                        end: true,  perm: 'perm_frequencias',  area: 'Faturamento' },
  { to: '/fechamentos',            label: 'Fechamento',       icon: PackageCheck,     roles: BACKOFFICE,                        end: true,  perm: 'perm_fechamentos',  area: 'Faturamento' },
  { to: '/fiscal/config',          label: 'Fiscal',           icon: SlidersHorizontal, roles: ['contabil', 'gestao', 'financeiro'], end: true, perm: 'perm_fiscal',    area: 'Fiscal' },
  { to: '/notas',                  label: 'Notas',            icon: FileText,         roles: BACKOFFICE,                        end: true,  perm: 'perm_notas',        area: 'Fiscal' },
  { to: '/notas/lote',             label: 'Lotes NFS-e',      icon: Layers,           roles: ['operacao', 'gestao', 'contabil', 'financeiro'], end: true, perm: 'perm_notas_lote', area: 'Fiscal' },
  { to: '/repasses',               label: 'Repasses',         icon: Banknote,         roles: BACKOFFICE,                        end: true,  perm: 'perm_repasses',     area: 'Financeiro' },
  { to: '/conciliacao/upload',     label: 'Upload Extrato',   icon: Upload,           roles: BACKOFFICE,                        end: true,  perm: 'perm_conciliacao',  area: 'Financeiro' },
  { to: '/conciliacao/assistida',  label: 'Conciliação',      icon: ArrowLeftRight,   roles: BACKOFFICE,                        end: true,  perm: 'perm_conciliacao',  area: 'Financeiro' },
  { to: '/conciliacao/caixa',      label: 'Posição de Caixa', icon: Wallet,           roles: BACKOFFICE,                        end: true,  perm: 'perm_caixa',        area: 'Financeiro' },
  { to: '/financeiro/ledger',      label: 'Extrato Ledger',   icon: BookOpen,         roles: ['financeiro', 'gestao', 'contabil'], end: true, perm: 'perm_ledger',     area: 'Financeiro' },
  { to: '/gestao',                 label: 'Gestão',           icon: BarChart3,        roles: ['gestao'],                        end: true,  perm: 'perm_gestao',       area: 'Gestão' },
  { to: '/usuarios',               label: 'Usuários',         icon: Users,            roles: ['gestao'],                        end: true,  perm: 'perm_usuarios',     area: 'Gestão' },
]

export interface PermCatalogEntry {
  perm:  string
  label: string
  area:  Area
}

/**
 * Catálogo de permissões deduplicado por `perm` (15 entradas — `perm_medicos` e
 * `perm_conciliacao` cobrem 2 telas cada, ver ADR-004). Usado no checklist do
 * `PerfilFormModal` (PERFIL-07): um checkbox por permissão, não por tela.
 */
export const permCatalog: PermCatalogEntry[] = (() => {
  const porPerm = new Map<string, { labels: string[]; area: Area }>()
  for (const item of navItems) {
    if (!item.perm || !item.area) continue
    const atual = porPerm.get(item.perm)
    if (atual) atual.labels.push(item.label)
    else porPerm.set(item.perm, { labels: [item.label], area: item.area })
  }
  return Array.from(porPerm.entries()).map(([perm, { labels, area }]) => ({
    perm,
    label: labels.join(' / '),
    area,
  }))
})()
