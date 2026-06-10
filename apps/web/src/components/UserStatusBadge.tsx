interface UserStatusBadgeProps {
  ativo:    boolean
  pendente: boolean
}

export function UserStatusBadge({ ativo, pendente }: UserStatusBadgeProps) {
  if (!ativo) {
    return (
      <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-700">
        Inativo
      </span>
    )
  }
  if (pendente) {
    return (
      <span className="inline-flex items-center rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-700">
        Convite enviado
      </span>
    )
  }
  return (
    <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-700">
      Ativo
    </span>
  )
}
