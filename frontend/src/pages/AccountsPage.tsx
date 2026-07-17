import { useMemo, useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  createAccount,
  deleteAccount,
  listAccounts,
  updateAccount,
  type Account,
  type AccountUpsertRequest,
} from '../api/accounts'
import { ApiError, getErrorMessage } from '../api/client'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FeedbackSnackbar, type SnackbarState } from '../components/FeedbackSnackbar'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

const accountsQueryKey = ['accounts'] as const

export function AccountsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim(), 300)
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)
  // null = Dialog zu, { account: null } = Anlage, { account } = Bearbeitung.
  const [editor, setEditor] = useState<{ account: Account | null } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Account | null>(null)

  const accountsQuery = useInfiniteQuery({
    queryKey: [...accountsQueryKey, 'list', q],
    queryFn: ({ pageParam, signal }) => listAccounts({ q: q || undefined, cursor: pageParam }, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
  const accounts = useMemo(
    () => accountsQuery.data?.pages.flatMap((page) => page.items) ?? [],
    [accountsQuery.data],
  )

  const deleteMutation = useMutation({
    mutationFn: (account: Account) => deleteAccount(account.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: accountsQueryKey })
      setSnackbar({ message: t('accounts.deleteSuccess'), severity: 'success' })
      setDeleteTarget(null)
    },
    onError: (error) => {
      setSnackbar({
        message:
          error instanceof ApiError && error.status === 403
            ? t('common.forbidden')
            : t('accounts.deleteError', { message: getErrorMessage(error) }),
        severity: 'error',
      })
      setDeleteTarget(null)
    },
  })

  return (
    <>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h5" component="h2">
          {t('accounts.heading')}
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditor({ account: null })}>
          {t('accounts.create')}
        </Button>
      </Stack>

      <TextField
        label={t('common.search')}
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        size="small"
        sx={{ mb: 2, width: 320 }}
      />

      {accountsQuery.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress aria-label={t('accounts.loading')} />
        </Box>
      )}
      {accountsQuery.isError && (
        <Alert severity="error">
          {t('accounts.loadError', { message: getErrorMessage(accountsQuery.error) })}
        </Alert>
      )}
      {accountsQuery.isSuccess && (
        <>
          <TableContainer component={Paper}>
            <Table size="small" aria-label={t('accounts.heading')}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('accounts.columns.name')}</TableCell>
                  <TableCell>{t('accounts.columns.industry')}</TableCell>
                  <TableCell>{t('accounts.columns.city')}</TableCell>
                  <TableCell>{t('accounts.columns.country')}</TableCell>
                  <TableCell align="right">{t('common.actions')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {accounts.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      <Typography color="text.secondary" py={2}>
                        {t('accounts.empty')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  accounts.map((account) => (
                    <TableRow key={account.id} hover>
                      <TableCell>{account.name}</TableCell>
                      <TableCell>{account.industry || '—'}</TableCell>
                      <TableCell>{account.city || '—'}</TableCell>
                      <TableCell>{account.country || '—'}</TableCell>
                      <TableCell align="right">
                        <IconButton
                          size="small"
                          aria-label={t('accounts.rowActions.edit')}
                          onClick={() => setEditor({ account })}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          aria-label={t('accounts.rowActions.delete')}
                          onClick={() => setDeleteTarget(account)}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
          {accountsQuery.hasNextPage && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
              <Button
                onClick={() => accountsQuery.fetchNextPage()}
                disabled={accountsQuery.isFetchingNextPage}
              >
                {t('common.loadMore')}
              </Button>
            </Box>
          )}
        </>
      )}

      {editor !== null && (
        <AccountDialog
          account={editor.account}
          onClose={() => setEditor(null)}
          onNotify={setSnackbar}
        />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        title={t('accounts.deleteTitle')}
        body={t('accounts.deleteBody', { name: deleteTarget?.name ?? '' })}
        confirmLabel={t('common.delete')}
        pending={deleteMutation.isPending}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
      />

      <FeedbackSnackbar snackbar={snackbar} onClose={() => setSnackbar(null)} />
    </>
  )
}

interface AccountDialogProps {
  /** null = neuen Account anlegen, sonst bestehenden bearbeiten. */
  account: Account | null
  onClose: () => void
  onNotify: (snackbar: SnackbarState) => void
}

const optionalFields = ['industry', 'website', 'street', 'postalCode', 'city', 'country'] as const
type OptionalField = (typeof optionalFields)[number]

function AccountDialog({ account, onClose, onNotify }: AccountDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const isEdit = account !== null
  const [name, setName] = useState(account?.name ?? '')
  const [values, setValues] = useState<Record<OptionalField, string>>({
    industry: account?.industry ?? '',
    website: account?.website ?? '',
    street: account?.street ?? '',
    postalCode: account?.postalCode ?? '',
    city: account?.city ?? '',
    country: account?.country ?? '',
  })
  const [submitted, setSubmitted] = useState(false)

  const mutation = useMutation({
    mutationFn: (request: AccountUpsertRequest) =>
      isEdit ? updateAccount(account.id, request) : createAccount(request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: accountsQueryKey })
      onNotify({
        message: t(isEdit ? 'accounts.updateSuccess' : 'accounts.createSuccess'),
        severity: 'success',
      })
      onClose()
    },
    onError: (error) => {
      onNotify({
        message:
          error instanceof ApiError && error.status === 403
            ? t('common.forbidden')
            : t(isEdit ? 'accounts.updateError' : 'accounts.createError', {
                message: getErrorMessage(error),
              }),
        severity: 'error',
      })
    },
  })

  const nameMissing = name.trim() === ''

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (nameMissing) {
      return
    }
    // Beim Bearbeiten werden Leerwerte mitgesendet (PATCH leert das Feld),
    // beim Anlegen weggelassen.
    const optional = (value: string) => (isEdit ? value.trim() : value.trim() || undefined)
    mutation.mutate({
      name: name.trim(),
      industry: optional(values.industry),
      website: optional(values.website),
      street: optional(values.street),
      postalCode: optional(values.postalCode),
      city: optional(values.city),
      country: optional(values.country),
    })
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t(isEdit ? 'accounts.form.editTitle' : 'accounts.form.createTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label={t('accounts.form.name')}
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
              autoFocus
              error={submitted && nameMissing}
              helperText={submitted && nameMissing ? t('accounts.form.nameRequired') : undefined}
            />
            {optionalFields.map((field) => (
              <TextField
                key={field}
                label={t(`accounts.form.${field}`)}
                value={values[field]}
                onChange={(event) =>
                  setValues((prev) => ({ ...prev, [field]: event.target.value }))
                }
              />
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {t(isEdit ? 'common.save' : 'common.create')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
