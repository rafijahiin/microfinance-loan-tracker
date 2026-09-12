/** Money arrives from the API as a decimal string, deliberately: parsing it to
 *  a JavaScript number would push it through a binary float, and 0.1 + 0.2 is
 *  the reason ledgers do not use those. It is only converted here, at the last
 *  moment, for display. */
export function taka(value: string | number): string {
  const n = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(n)) return String(value)
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function percent(fraction: string | number, digits = 1): string {
  const n = typeof fraction === 'number' ? fraction : Number(fraction)
  if (!Number.isFinite(n)) return String(fraction)
  return `${(n * 100).toFixed(digits)}%`
}

/** An instant, to the minute. Seconds are noise on an activity feed, and the
 *  date is what someone reconciling a receipt is looking for. */
export function when(iso: string | null): string {
  if (!iso) return '\u2014'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function day(iso: string | null): string {
  if (!iso) return '\u2014'
  const [y, m, d] = iso.split('-')
  return y && m && d ? `${d}/${m}/${y}` : iso
}
