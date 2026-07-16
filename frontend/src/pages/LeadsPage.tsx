import { useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { ChipProps } from '@mui/material/Chip'
import AddIcon from '@mui/icons-material/Add'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createLead, listLeads, type Lead, type LeadStatus } from '../api/leads'
import { getErrorMessage } from '../api/client'

const leadsQueryKey = ['leads'] as const

const statusColors: Record<LeadStatus, ChipProps['color']> = {
  NEW: 'default',
  ASSIGNED: 'info',
  CONTACTED: 'primary',
  QUALIFIED: 'success',
  DISQUALIFIED: 'error',
  CONVERTED: 'success',
}

interface SnackbarState {
  message: string
  severity: 'success' | 'error'
}

export function LeadsPage() {
  const { t } = useTranslation()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)

  const leadsQuery = useQuery({
    queryKey: leadsQueryKey,
    queryFn: ({ signal }) => listLeads(signal),
  })

  return (
    <>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h5" component="h2">
          {t('leads.heading')}
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          {t('leads.create')}
        </Button>
      </Stack>

      {leadsQuery.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress aria-label={t('leads.loading')} />
        </Box>
      )}
      {leadsQuery.isError && (
        <Alert severity="error">
          {t('leads.loadError', { message: getErrorMessage(leadsQuery.error) })}
        </Alert>
      )}
      {leadsQuery.isSuccess && <LeadsTable leads={leadsQuery.data.items} />}

      <LeadCreateDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onNotify={setSnackbar}
      />

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={6000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {snackbar ? (
          <Alert severity={snackbar.severity} onClose={() => setSnackbar(null)} variant="filled">
            {snackbar.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </>
  )
}

function LeadsTable({ leads }: { leads: Lead[] }) {
  const { t } = useTranslation()

  return (
    <TableContainer component={Paper}>
      <Table size="small" aria-label={t('leads.heading')}>
        <TableHead>
          <TableRow>
            <TableCell>{t('leads.columns.title')}</TableCell>
            <TableCell>{t('leads.columns.company')}</TableCell>
            <TableCell>{t('leads.columns.lastName')}</TableCell>
            <TableCell>{t('leads.columns.email')}</TableCell>
            <TableCell>{t('leads.columns.status')}</TableCell>
            <TableCell>{t('leads.columns.owner')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {leads.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6} align="center">
                <Typography color="text.secondary" py={2}>
                  {t('leads.empty')}
                </Typography>
              </TableCell>
            </TableRow>
          ) : (
            leads.map((lead) => (
              <TableRow key={lead.id} hover>
                <TableCell>{lead.title}</TableCell>
                <TableCell>{lead.companyName ?? '—'}</TableCell>
                <TableCell>{lead.lastName ?? '—'}</TableCell>
                <TableCell>{lead.email ?? '—'}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={statusColors[lead.status]}
                    label={t(`leads.status.${lead.status}`)}
                  />
                </TableCell>
                <TableCell>{lead.ownerId ?? t('leads.unassigned')}</TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

interface LeadCreateDialogProps {
  open: boolean
  onClose: () => void
  onNotify: (snackbar: SnackbarState) => void
}

function LeadCreateDialog({ open, onClose, onNotify }: LeadCreateDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const resetAndClose = () => {
    setTitle('')
    setCompanyName('')
    setLastName('')
    setEmail('')
    setSubmitted(false)
    onClose()
  }

  const mutation = useMutation({
    mutationFn: createLead,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: leadsQueryKey })
      onNotify({ message: t('leads.createSuccess'), severity: 'success' })
      resetAndClose()
    },
    onError: (error) => {
      onNotify({
        message: t('leads.createError', { message: getErrorMessage(error) }),
        severity: 'error',
      })
    },
  })

  const titleMissing = title.trim() === ''
  // Backend-Validierung gespiegelt: mindestens eines von Firma/Nachname muss gesetzt sein.
  const companyOrLastNameMissing = companyName.trim() === '' && lastName.trim() === ''

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (titleMissing || companyOrLastNameMissing) {
      return
    }
    mutation.mutate({
      title: title.trim(),
      companyName: companyName.trim() || undefined,
      lastName: lastName.trim() || undefined,
      email: email.trim() || undefined,
    })
  }

  return (
    <Dialog open={open} onClose={resetAndClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t('leads.form.dialogTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label={t('leads.form.title')}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              required
              autoFocus
              error={submitted && titleMissing}
              helperText={submitted && titleMissing ? t('leads.form.titleRequired') : undefined}
            />
            <TextField
              label={t('leads.form.company')}
              value={companyName}
              onChange={(event) => setCompanyName(event.target.value)}
              error={submitted && companyOrLastNameMissing}
            />
            <TextField
              label={t('leads.form.lastName')}
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
              error={submitted && companyOrLastNameMissing}
            />
            {submitted && companyOrLastNameMissing && (
              <Alert severity="warning">{t('leads.form.companyOrLastNameRequired')}</Alert>
            )}
            <TextField
              label={t('leads.form.email')}
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={resetAndClose}>{t('leads.form.cancel')}</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {t('leads.form.submit')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
