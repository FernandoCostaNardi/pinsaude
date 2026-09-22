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
}

export const navItems: NavItem[] = [
  // ── Portal do Médico ─────────────────────────────────────────────────────────
  { to: '/portal/dashboard',     label: 'Meu Portal',        icon: HeartPulse,       roles: ['medico'], end: true  },
  { to: '/portal/extrato',       label: 'Extrato',           icon: TrendingUp,       roles: ['medico'], end: true  },
  { to: '/portal/producao/nova', label: 'Informar Produção', icon: PlusCircle,       roles: ['medico'], end: true  },
  { to: '/portal/frequencias',   label: 'Frequências',       icon: CalendarDays,     roles: ['medico'], end: true  },
  // ── Backoffice ───────────────────────────────────────────────────────────────
  { to: '/',                       label: 'Dashboard',        icon: LayoutDashboard,  roles: BACKOFFICE,                        end: true  },
  { to: '/medicos',                label: 'Médicos',          icon: Stethoscope,      roles: ['gestao', 'operacao'],            end: false, perm: 'perm_medicos' },
  { to: '/medicos/aprovacao',      label: 'Aprovação',        icon: ClipboardCheck,   roles: ['gestao', 'operacao'],            end: true,  perm: 'perm_medicos' },
  { to: '/empresas',               label: 'Empresas',         icon: Building2,        roles: ['gestao'],                        end: true,  perm: 'perm_empresas' },
  { to: '/tomadores',              label: 'Tomadores',        icon: Hospital,         roles: BACKOFFICE,                        end: true,  perm: 'perm_tomadores' },
  { to: '/producao',               label: 'Produção',         icon: ClipboardList,    roles: BACKOFFICE,                        end: false, perm: 'perm_producao' },
  { to: '/frequencias',            label: 'Frequências',      icon: CalendarDays,     roles: BACKOFFICE,                        end: true,  perm: 'perm_frequencias' },
  { to: '/fechamentos',            label: 'Fechamento',       icon: PackageCheck,     roles: BACKOFFICE,                        end: true,  perm: 'perm_fechamentos' },
  { to: '/fiscal/config',          label: 'Fiscal',           icon: SlidersHorizontal, roles: ['contabil', 'gestao', 'financeiro'], end: true, perm: 'perm_fiscal' },
  { to: '/notas',                  label: 'Notas',            icon: FileText,         roles: BACKOFFICE,                        end: true,  perm: 'perm_notas' },
  { to: '/notas/lote',             label: 'Lotes NFS-e',      icon: Layers,           roles: ['operacao', 'gestao', 'contabil', 'financeiro'], end: true, perm: 'perm_notas_lote' },
  { to: '/repasses',               label: 'Repasses',         icon: Banknote,         roles: BACKOFFICE,                        end: true,  perm: 'perm_repasses' },
  { to: '/conciliacao/upload',     label: 'Upload Extrato',   icon: Upload,           roles: BACKOFFICE,                        end: true,  perm: 'perm_conciliacao' },
  { to: '/conciliacao/assistida',  label: 'Conciliação',      icon: ArrowLeftRight,   roles: BACKOFFICE,                        end: true,  perm: 'perm_conciliacao' },
  { to: '/conciliacao/caixa',      label: 'Posição de Caixa', icon: Wallet,           roles: BACKOFFICE,                        end: true,  perm: 'perm_caixa' },
  { to: '/financeiro/ledger',      label: 'Extrato Ledger',   icon: BookOpen,         roles: ['financeiro', 'gestao', 'contabil'], end: true, perm: 'perm_ledger' },
  { to: '/gestao',                 label: 'Gestão',           icon: BarChart3,        roles: ['gestao'],                        end: true,  perm: 'perm_gestao' },
  { to: '/usuarios',               label: 'Usuários',         icon: Users,            roles: ['gestao'],                        end: true,  perm: 'perm_usuarios' },
]
