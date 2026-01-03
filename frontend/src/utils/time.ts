import dayjs from 'dayjs'

export function formatDateTime(
  value: string | number | Date | null | undefined,
  format = 'YYYY-MM-DD HH:mm',
): string {
  if (value == null) return '-'
  const d = dayjs(value)
  if (!d.isValid()) return '-'
  return d.format(format)
}

