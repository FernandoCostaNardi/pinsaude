interface UnderConstructionProps {
  title: string
  epic?: string
}

export function UnderConstruction({ title, epic }: UnderConstructionProps) {
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">{title}</h1>
      </div>
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <span className="text-6xl mb-4">🚧</span>
        <h2 className="text-lg font-semibold text-gray-700">Em construção</h2>
        <p className="text-sm text-gray-400 mt-1 max-w-xs">
          Esta seção está sendo desenvolvida e estará disponível em breve.
        </p>
        {epic && (
          <span className="mt-4 inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-primary-50 text-primary-700">
            {epic}
          </span>
        )}
      </div>
    </div>
  )
}
