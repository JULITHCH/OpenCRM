import Typography from '@mui/material/Typography'
import { useTranslation } from 'react-i18next'

interface PlaceholderPageProps {
  /** i18n-Schlüssel für die Seitenüberschrift, z. B. "nav.dashboard". */
  titleKey: string
}

export function PlaceholderPage({ titleKey }: PlaceholderPageProps) {
  const { t } = useTranslation()
  return (
    <>
      <Typography variant="h5" component="h2" gutterBottom>
        {t(titleKey)}
      </Typography>
      <Typography color="text.secondary">{t('placeholder.body')}</Typography>
    </>
  )
}
