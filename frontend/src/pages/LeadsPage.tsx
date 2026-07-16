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
import FormControl from '@mui/material/FormControl'
import IconButton from '@mui/material/IconButton'
import InputLabel from '@mui/material/InputLabel'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
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
import MoreVertIcon from '@mui/icons-material/MoreVert'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import {
  assignLead,
  createLead,
  disqualifyLead,
  listLeads,
  markLeadContacted,
  qualifyLead,
  type Lead,
  type LeadStatus,
} from '../api/leads'
import { ApiError, getErrorMessage } from '../api/client'
import { FeedbackSnackbar, type SnackbarState } from '../components/FeedbackSnackbar'
import { useUserNames, useUsers } from '../hooks/useUsers'

const leadsQueryKey = ['leads'] as const

const statusColors: Record<LeadStatus, ChipProps['color']> = {
  NEW: 'default',
  ASSIGNED: 'info',
  CONTACTED: 'primary',
  QUALIFIED: 'success',
  DISQUALIFIED: 'error',
  CONVERTED: 'success',
}

const allStatuses: LeadStatus[] = [
  'NEW',
  'ASSIGNED',
  'CONTACTED',
  'QUALIFIED',
  'DISQUALIFIED',
  'CONVERTED',
]

/** Nicht-terminale Status laut Lifecycle (docs/06): aus ihnen ist Disqualifikation erlaubt. */
const nonTerminalStatuses: LeadStatus[] = ['NEW', 'ASSIGNED', 'CONTACTED', 'QUALIFIED']

/** Sinnvolle Aktionen je Lead entsprechend der erlaubten Statusübergänge. */
function leadActions(lead: Lead) {
  return {
    assign: lead.status === 'NEW',
    contacted: lead.status === 'ASSIGNED',
    qualify: lead.status === 'CONTACTED',
    disqualify: nonTerminalStatuses.includes(lead.status),
  }
}

function actionErrorMessage(t: TFunction, error: unknown, errorKey: string): string {
  if (error instanceof ApiError && error.status === 403) {
    return t('common.forbidden')
  }
  return t(errorKey, { message: getErrorMessage(error) })
}

export function LeadsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)
  const [statusFilter, setStatusFilter] = useState<LeadStatus | 'ALL'>('ALL')
  const [assignTarget, setAssignTarget] = useState<Lead | null>(null)
  const [disqualifyTarget, setDisqualifyTarget] = useState<Lead | null>(null)

  const leadsQuery = useQuery({
    queryKey: [...leadsQueryKey, 'list', statusFilter],
    queryFn: ({ signal }) =>
      listLeads({ status: statusFilter === 'ALL' ? undefined : statusFilter }, signal),
  })

  const userNames = useUserNames()

  const transitionMutation = useMutation({
    mutationFn: ({ lead, action }: { lead: Lead; action: 'contacted' | 'qualify' }) =>
      action === 'contacted' ? markLeadContacted(lead.id) : qualifyLead(lead.id),
    onSuccess: async (_lead, { action }) => {
      await queryClient.invalidateQueries({ queryKey: leadsQueryKey })
      setSnackbar({
        message: t(
          action === 'contacted' ? 'leads.actions.contactedSuccess' : 'leads.actions.qualifySuccess',
        ),
        severity: 'success',
      })
    },
    onError: (error) => {
      setSnackbar({
        message: actionErrorMessage(t, error, 'leads.actions.actionError'),
        severity: 'error',
      })
    },
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

      <FormControl size="small" sx={{ mb: 2, minWidth: 220 }}>
        <InputLabel id="lead-status-filter-label">{t('leads.filter.status')}</InputLabel>
        <Select
          labelId="lead-status-filter-label"
          label={t('leads.filter.status')}
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as LeadStatus | 'ALL')}
        >
          <MenuItem value="ALL">{t('leads.filter.all')}</MenuItem>
          {allStatuses.map((status) => (
            <MenuItem key={status} value={status}>
              {t(`leads.status.${status}`)}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

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
      {leadsQuery.isSuccess && (
        <LeadsTable
          leads={leadsQuery.data.items}
          userNames={userNames}
          onAssign={setAssignTarget}
          onContacted={(lead) => transitionMutation.mutate({ lead, action: 'contacted' })}
          onQualify={(lead) => transitionMutation.mutate({ lead, action: 'qualify' })}
          onDisqualify={setDisqualifyTarget}
        />
      )}

      <LeadCreateDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onNotify={setSnackbar}
      />
      {assignTarget && (
        <AssignDialog
          lead={assignTarget}
          onClose={() => setAssignTarget(null)}
          onNotify={setSnackbar}
        />
      )}
      {disqualifyTarget && (
        <DisqualifyDialog
          lead={disqualifyTarget}
          onClose={() => setDisqualifyTarget(null)}
          onNotify={setSnackbar}
        />
      )}

      <FeedbackSnackbar snackbar={snackbar} onClose={() => setSnackbar(null)} />
    </>
  )
}

interface LeadsTableProps {
  leads: Lead[]
  userNames: Map<string, string>
  onAssign: (lead: Lead) => void
  onContacted: (lead: Lead) => void
  onQualify: (lead: Lead) => void
  onDisqualify: (lead: Lead) => void
}

function LeadsTable({
  leads,
  userNames,
  onAssign,
  onContacted,
  onQualify,
  onDisqualify,
}: LeadsTableProps) {
  const { t } = useTranslation()
  const [menu, setMenu] = useState<{ anchorEl: HTMLElement; lead: Lead } | null>(null)

  const closeMenu = () => setMenu(null)
  const runAction = (action: (lead: Lead) => void) => {
    if (menu) {
      action(menu.lead)
    }
    closeMenu()
  }
  const menuActions = menu ? leadActions(menu.lead) : null

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
            <TableCell align="right">{t('common.actions')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {leads.length === 0 ? (
            <TableRow>
              <TableCell colSpan={7} align="center">
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
                <TableCell>
                  {lead.ownerId
                    ? (userNames.get(lead.ownerId) ?? lead.ownerId)
                    : t('leads.unassigned')}
                </TableCell>
                <TableCell align="right">
                  {Object.values(leadActions(lead)).some(Boolean) && (
                    <IconButton
                      size="small"
                      aria-label={t('leads.actions.menu')}
                      onClick={(event) => setMenu({ anchorEl: event.currentTarget, lead })}
                    >
                      <MoreVertIcon fontSize="small" />
                    </IconButton>
                  )}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
      <Menu anchorEl={menu?.anchorEl ?? null} open={menu !== null} onClose={closeMenu}>
        {menuActions?.assign && (
          <MenuItem onClick={() => runAction(onAssign)}>{t('leads.actions.assign')}</MenuItem>
        )}
        {menuActions?.contacted && (
          <MenuItem onClick={() => runAction(onContacted)}>{t('leads.actions.contacted')}</MenuItem>
        )}
        {menuActions?.qualify && (
          <MenuItem onClick={() => runAction(onQualify)}>{t('leads.actions.qualify')}</MenuItem>
        )}
        {menuActions?.disqualify && (
          <MenuItem onClick={() => runAction(onDisqualify)}>
            {t('leads.actions.disqualify')}
          </MenuItem>
        )}
      </Menu>
    </TableContainer>
  )
}

interface LeadActionDialogProps {
  lead: Lead
  onClose: () => void
  onNotify: (snackbar: SnackbarState) => void
}

function AssignDialog({ lead, onClose, onNotify }: LeadActionDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const usersQuery = useUsers()
  const [userId, setUserId] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const mutation = useMutation({
    mutationFn: () => assignLead(lead.id, userId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: leadsQueryKey })
      onNotify({ message: t('leads.assign.success'), severity: 'success' })
      onClose()
    },
    onError: (error) => {
      onNotify({
        message: actionErrorMessage(t, error, 'leads.assign.error'),
        severity: 'error',
      })
    },
  })

  const userMissing = userId === ''

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (userMissing) {
      return
    }
    mutation.mutate()
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t('leads.assign.dialogTitle', { title: lead.title })}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            {usersQuery.isError && (
              <Alert severity="error">
                {t('leads.assign.usersLoadError', { message: getErrorMessage(usersQuery.error) })}
              </Alert>
            )}
            <FormControl required error={submitted && userMissing}>
              <InputLabel id="assign-user-label">{t('leads.assign.user')}</InputLabel>
              <Select
                labelId="assign-user-label"
                label={t('leads.assign.user')}
                value={userId}
                onChange={(event) => setUserId(event.target.value)}
              >
                {(usersQuery.data ?? []).map((user) => (
                  <MenuItem key={user.id} value={user.id}>
                    {user.displayName}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            {submitted && userMissing && (
              <Alert severity="warning">{t('leads.assign.userRequired')}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {t('leads.assign.submit')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}

function DisqualifyDialog({ lead, onClose, onNotify }: LeadActionDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const mutation = useMutation({
    mutationFn: () => disqualifyLead(lead.id, reason.trim()),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: leadsQueryKey })
      onNotify({ message: t('leads.disqualify.success'), severity: 'success' })
      onClose()
    },
    onError: (error) => {
      onNotify({
        message: actionErrorMessage(t, error, 'leads.disqualify.error'),
        severity: 'error',
      })
    },
  })

  const reasonMissing = reason.trim() === ''

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (reasonMissing) {
      return
    }
    mutation.mutate()
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t('leads.disqualify.dialogTitle', { title: lead.title })}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label={t('leads.disqualify.reason')}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              required
              autoFocus
              multiline
              minRows={2}
              error={submitted && reasonMissing}
              helperText={
                submitted && reasonMissing ? t('leads.disqualify.reasonRequired') : undefined
              }
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" variant="contained" color="error" disabled={mutation.isPending}>
            {t('leads.disqualify.submit')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
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
