import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

/** Reine Datumsangaben (YYYY-MM-DD) lokal parsen, sonst schiebt UTC-Parsing den Tag. */
function parseDate(value: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (match) {
    return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  }
  return new Date(value)
}

/** Locale-abhängige Formatierer für Währung, Prozent, Zahlen und Datumsangaben. */
export function useFormatters() {
  const { i18n } = useTranslation()
  const locale = i18n.language
  return useMemo(() => {
    const currencyCache = new Map<string, Intl.NumberFormat>()
    const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 1 })
    const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 })
    const compact = new Intl.NumberFormat(locale, {
      notation: 'compact',
      maximumFractionDigits: 1,
    })
    const date = new Intl.DateTimeFormat(locale, { dateStyle: 'medium' })
    const shortDate = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'numeric' })
    const dateTime = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' })
    return {
      formatCurrency(value: number | null | undefined, currency = 'EUR'): string {
        if (value == null) {
          return '—'
        }
        let formatter = currencyCache.get(currency)
        if (!formatter) {
          formatter = new Intl.NumberFormat(locale, { style: 'currency', currency })
          currencyCache.set(currency, formatter)
        }
        return formatter.format(value)
      },
      formatPercent: (value: number) => percent.format(value),
      formatNumber: (value: number) => number.format(value),
      formatCompactNumber: (value: number) => compact.format(value),
      formatDate: (value: string | null | undefined) =>
        value ? date.format(parseDate(value)) : '—',
      formatShortDate: (value: string) => shortDate.format(parseDate(value)),
      formatDateTime: (value: string | null | undefined) =>
        value ? dateTime.format(parseDate(value)) : '—',
    }
  }, [locale])
}
