import { useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getTenantSettings, patchTenantSettings } from '../api/tenantSettings'
import { ApiError, getErrorMessage } from '../api/client'
import { FeedbackSnackbar, type SnackbarState } from '../components/FeedbackSnackbar'

const tenantSettingsQueryKey = ['tenant-settings'] as const

export function SettingsPage() {
  const { t } = useTranslation()
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)

  const settingsQuery = useQuery({
    queryKey: tenantSettingsQueryKey,
    queryFn: ({ signal }) => getTenantSettings(signal),
    // 403 (kein tenant-admin) ist endgültig — nicht erneut versuchen.
    retry: (failureCount, error) =>
      !(error instanceof ApiError && error.status === 403) && failureCount < 3,
  })
  const forbidden =
    settingsQuery.error instanceof ApiError && settingsQuery.error.status === 403

  return (
    <>
      <Typography variant="h5" component="h2" mb={2}>
        {t('settings.heading')}
      </Typography>

      {settingsQuery.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress aria-label={t('settings.loading')} />
        </Box>
      )}
      {forbidden && <Alert severity="info">{t('settings.adminOnly')}</Alert>}
      {settingsQuery.isError && !forbidden && (
        <Alert severity="error">
          {t('settings.loadError', { message: getErrorMessage(settingsQuery.error) })}
        </Alert>
      )}
      {settingsQuery.isSuccess && (
        <Stack spacing={3} sx={{ maxWidth: 480 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              {t('settings.tenant.title')}
            </Typography>
            <InfoRow label={t('settings.tenant.name')} value={settingsQuery.data.name} />
            <InfoRow label={t('settings.tenant.plan')} value={settingsQuery.data.plan} />
            <InfoRow
              label={t('settings.tenant.currency')}
              value={settingsQuery.data.defaultCurrency}
            />
          </Paper>
          <SettingsForm settings={settingsQuery.data.settings} onNotify={setSnackbar} />
        </Stack>
      )}

      <FeedbackSnackbar snackbar={snackbar} onClose={() => setSnackbar(null)} />
    </>
  )
}

interface InfoRowProps {
  label: string
  value: string
}

function InfoRow({ label, value }: InfoRowProps) {
  return (
    <Stack direction="row" justifyContent="space-between" py={0.5}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography>{value}</Typography>
    </Stack>
  )
}

function numberOrEmpty(value: unknown): string {
  return typeof value === 'number' || typeof value === 'string' ? String(value) : ''
}

interface SettingsFormProps {
  settings: Record<string, unknown>
  onNotify: (snackbar: SnackbarState) => void
}

function SettingsForm({ settings, onNotify }: SettingsFormProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [slaHours, setSlaHours] = useState(numberOrEmpty(settings['sla_hours']))
  const [retentionMonths, setRetentionMonths] = useState(
    numberOrEmpty(settings['lead_retention_months']),
  )
  const [submitted, setSubmitted] = useState(false)

  const mutation = useMutation({
    mutationFn: patchTenantSettings,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: tenantSettingsQueryKey })
      onNotify({ message: t('settings.saveSuccess'), severity: 'success' })
    },
    onError: (error) => {
      onNotify({
        message:
          error instanceof ApiError && error.status === 403
            ? t('settings.adminOnly')
            : t('settings.saveError', { message: getErrorMessage(error) }),
        severity: 'error',
      })
    },
  })

  const isInvalid = (value: string) =>
    value.trim() !== '' && (!Number.isFinite(Number(value)) || Number(value) < 0)
  const slaInvalid = isInvalid(slaHours)
  const retentionInvalid = isInvalid(retentionMonths)
  const nothingToSave = slaHours.trim() === '' && retentionMonths.trim() === ''

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (slaInvalid || retentionInvalid || nothingToSave) {
      return
    }
    // Nur befüllte Felder senden — der PATCH mergt in die bestehenden Settings.
    const payload: Record<string, unknown> = {}
    if (slaHours.trim() !== '') {
      payload['sla_hours'] = Number(slaHours)
    }
    if (retentionMonths.trim() !== '') {
      payload['lead_retention_months'] = Number(retentionMonths)
    }
    mutation.mutate(payload)
  }

  return (
    <Paper component="form" onSubmit={handleSubmit} noValidate sx={{ p: 2 }}>
      <Typography variant="subtitle1" gutterBottom>
        {t('settings.form.title')}
      </Typography>
      <Stack spacing={2} mt={1}>
        <TextField
          label={t('settings.form.slaHours')}
          type="number"
          value={slaHours}
          onChange={(event) => setSlaHours(event.target.value)}
          inputProps={{ min: 0 }}
          error={submitted && slaInvalid}
          helperText={submitted && slaInvalid ? t('settings.form.invalidNumber') : undefined}
        />
        <TextField
          label={t('settings.form.leadRetentionMonths')}
          type="number"
          value={retentionMonths}
          onChange={(event) => setRetentionMonths(event.target.value)}
          inputProps={{ min: 0 }}
          error={submitted && retentionInvalid}
          helperText={submitted && retentionInvalid ? t('settings.form.invalidNumber') : undefined}
        />
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            type="submit"
            variant="contained"
            disabled={mutation.isPending || nothingToSave}
          >
            {t('common.save')}
          </Button>
        </Box>
      </Stack>
    </Paper>
  )
}
