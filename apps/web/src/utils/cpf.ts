export function formatCpf(value: string): string {
  if (!value) return ''
  const d = value.replace(/\D/g, '').slice(0, 11)
  if (d.length <= 3) return d
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9, 11)}`
}

export function isValidCpf(value: string): boolean {
  if (!value) return false
  const d = value.replace(/\D/g, '')
  if (d.length !== 11) return false
  if (/^(\d)\1+$/.test(d)) return false

  function digit(str: string, weights: number[]): number {
    const sum = str.split('').reduce((acc, n, i) => acc + parseInt(n) * weights[i], 0)
    const rem = (sum * 10) % 11
    return rem >= 10 ? 0 : rem
  }

  const d1 = digit(d.slice(0, 9), [10, 9, 8, 7, 6, 5, 4, 3, 2])
  if (d1 !== parseInt(d[9])) return false
  const d2 = digit(d.slice(0, 10), [11, 10, 9, 8, 7, 6, 5, 4, 3, 2])
  return d2 === parseInt(d[10])
}
