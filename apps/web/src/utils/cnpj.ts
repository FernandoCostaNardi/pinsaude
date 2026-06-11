export function formatCnpj(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 14)
  if (digits.length <= 2) return digits
  if (digits.length <= 5) return `${digits.slice(0, 2)}.${digits.slice(2)}`
  if (digits.length <= 8) return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5)}`
  if (digits.length <= 12) return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8)}`
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`
}

export function isValidCnpj(value: string): boolean {
  const digits = value.replace(/\D/g, '')
  if (digits.length !== 14) return false
  if (/^(\d)\1{13}$/.test(digits)) return false

  const calc = (weights: number[]) => {
    const sum = weights.reduce((acc, w, i) => acc + parseInt(digits[i]) * w, 0)
    const r = sum % 11
    return r < 2 ? 0 : 11 - r
  }

  const d1 = calc([5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
  const d2 = calc([6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
  return parseInt(digits[12]) === d1 && parseInt(digits[13]) === d2
}
