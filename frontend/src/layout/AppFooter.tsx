import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { useTranslation } from 'react-i18next'

export function AppFooter() {
  const { t } = useTranslation()
  const year = new Date().getFullYear()

  return (
    <Box
      component="footer"
      sx={{
        mt: 'auto',
        py: 1.5,
        px: 3,
        display: 'flex',
        flexWrap: 'wrap',
        gap: 1,
        justifyContent: 'space-between',
        alignItems: 'center',
        borderTop: 1,
        borderColor: 'divider',
        color: 'text.secondary',
      }}
    >
      <Typography variant="body2" color="inherit">
        {'© '}
        {year}{' '}
        <Link href="https://julith.gmbh" target="_blank" rel="noopener noreferrer" color="inherit" underline="hover">
          JULITH GmbH
        </Link>
      </Typography>
      <Typography variant="body2" color="inherit" sx={{ fontFamily: 'monospace' }}>
        {t('footer.build', { sha: __GIT_SHA__ })}
      </Typography>
    </Box>
  )
}
