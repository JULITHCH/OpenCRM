import { useEffect, useMemo, useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Autocomplete from '@mui/material/Autocomplete'
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
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getAccount, listAccounts } from '../api/accounts'
import { ApiError, getErrorMessage } from '../api/client'
import {
  createContact,
  deleteContact,
  listContacts,
  updateContact,
  type Contact,
  type ContactUpsertRequest,
} from '../api/contacts'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FeedbackSnackbar, type SnackbarState } from '../components/FeedbackSnackbar'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

const contactsQueryKey = ['contacts'] as const

export function ContactsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim(), 300)
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)
  // null = Dialog zu, { contact: null } = Anlage, { contact } = Bearbeitung.
  const [editor, setEditor] = useState<{ contact: Contact | null } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Contact | null>(null)

  const contactsQuery = useInfiniteQuery({
    queryKey: [...contactsQueryKey, 'list', q],
    queryFn: ({ pageParam, signal }) => listContacts({ q: q || undefined, cursor: pageParam }, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
  const contacts = useMemo(
    () => contactsQuery.data?.pages.flatMap((page) => page.items) ?? [],
    [contactsQuery.data],
  )

  const deleteMutation = useMutation({
    mutationFn: (contact: Contact) => deleteContact(contact.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: contactsQueryKey })
      setSnackbar({ message: t('contacts.deleteSuccess'), severity: 'success' })
      setDeleteTarget(null)
    },
    onError: (error) => {
      setSnackbar({
        message:
          error instanceof ApiError && error.status === 403
            ? t('common.forbidden')
            : t('contacts.deleteError', { message: getErrorMessage(error) }),
        severity: 'error',
      })
      setDeleteTarget(null)
    },
  })

  return (
    <>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h5" component="h2">
          {t('contacts.heading')}
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditor({ contact: null })}>
          {t('contacts.create')}
        </Button>
      </Stack>

      <TextField
        label={t('common.search')}
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        size="small"
        sx={{ mb: 2, width: 320 }}
      />

      {contactsQuery.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress aria-label={t('contacts.loading')} />
        </Box>
      )}
      {contactsQuery.isError && (
        <Alert severity="error">
          {t('contacts.loadError', { message: getErrorMessage(contactsQuery.error) })}
        </Alert>
      )}
      {contactsQuery.isSuccess && (
        <>
          <TableContainer component={Paper}>
            <Table size="small" aria-label={t('contacts.heading')}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('contacts.columns.lastName')}</TableCell>
                  <TableCell>{t('contacts.columns.firstName')}</TableCell>
                  <TableCell>{t('contacts.columns.email')}</TableCell>
                  <TableCell>{t('contacts.columns.phone')}</TableCell>
                  <TableCell align="right">{t('common.actions')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {contacts.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      <Typography color="text.secondary" py={2}>
                        {t('contacts.empty')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  contacts.map((contact) => (
                    <TableRow key={contact.id} hover>
                      <TableCell>{contact.lastName}</TableCell>
                      <TableCell>{contact.firstName || '—'}</TableCell>
                      <TableCell>{contact.email || '—'}</TableCell>
                      <TableCell>{contact.phone || '—'}</TableCell>
                      <TableCell align="right">
                        <IconButton
                          size="small"
                          aria-label={t('contacts.rowActions.edit')}
                          onClick={() => setEditor({ contact })}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          aria-label={t('contacts.rowActions.delete')}
                          onClick={() => setDeleteTarget(contact)}
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
          {contactsQuery.hasNextPage && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
              <Button
                onClick={() => contactsQuery.fetchNextPage()}
                disabled={contactsQuery.isFetchingNextPage}
              >
                {t('common.loadMore')}
              </Button>
            </Box>
          )}
        </>
      )}

      {editor !== null && (
        <ContactDialog
          contact={editor.contact}
          onClose={() => setEditor(null)}
          onNotify={setSnackbar}
        />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        title={t('contacts.deleteTitle')}
        body={t('contacts.deleteBody', { name: deleteTarget?.lastName ?? '' })}
        confirmLabel={t('common.delete')}
        pending={deleteMutation.isPending}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
      />

      <FeedbackSnackbar snackbar={snackbar} onClose={() => setSnackbar(null)} />
    </>
  )
}

interface AccountOption {
  id: string
  name: string
}

interface ContactDialogProps {
  /** null = neuen Kontakt anlegen, sonst bestehenden bearbeiten. */
  contact: Contact | null
  onClose: () => void
  onNotify: (snackbar: SnackbarState) => void
}

function ContactDialog({ contact, onClose, onNotify }: ContactDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const isEdit = contact !== null
  const [lastName, setLastName] = useState(contact?.lastName ?? '')
  const [firstName, setFirstName] = useState(contact?.firstName ?? '')
  const [email, setEmail] = useState(contact?.email ?? '')
  const [phone, setPhone] = useState(contact?.phone ?? '')
  const [submitted, setSubmitted] = useState(false)

  const [accountSearch, setAccountSearch] = useState('')
  const debouncedAccountSearch = useDebouncedValue(accountSearch.trim(), 300)
  const [selectedAccount, setSelectedAccount] = useState<AccountOption | null>(null)
  const [accountTouched, setAccountTouched] = useState(false)

  const optionsQuery = useQuery({
    queryKey: ['accounts', 'options', debouncedAccountSearch],
    queryFn: ({ signal }) =>
      listAccounts({ q: debouncedAccountSearch || undefined, limit: 20 }, signal),
    staleTime: 30 * 1000,
  })

  // Beim Bearbeiten den zugeordneten Account nachladen, um das Autocomplete vorzubelegen.
  const prefillAccountId = contact?.accountId ?? null
  const prefillQuery = useQuery({
    queryKey: ['accounts', 'detail', prefillAccountId],
    queryFn: ({ signal }) => getAccount(prefillAccountId as string, signal),
    enabled: prefillAccountId !== null,
    staleTime: 60 * 1000,
  })
  useEffect(() => {
    if (!accountTouched && prefillQuery.data) {
      setSelectedAccount({ id: prefillQuery.data.id, name: prefillQuery.data.name })
    }
  }, [accountTouched, prefillQuery.data])

  const options = useMemo(() => {
    const items = (optionsQuery.data?.items ?? []).map((account) => ({
      id: account.id,
      name: account.name,
    }))
    if (selectedAccount && !items.some((option) => option.id === selectedAccount.id)) {
      return [selectedAccount, ...items]
    }
    return items
  }, [optionsQuery.data, selectedAccount])

  const mutation = useMutation({
    mutationFn: (request: ContactUpsertRequest) =>
      isEdit ? updateContact(contact.id, request) : createContact(request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: contactsQueryKey })
      onNotify({
        message: t(isEdit ? 'contacts.updateSuccess' : 'contacts.createSuccess'),
        severity: 'success',
      })
      onClose()
    },
    onError: (error) => {
      onNotify({
        message:
          error instanceof ApiError && error.status === 403
            ? t('common.forbidden')
            : t(isEdit ? 'contacts.updateError' : 'contacts.createError', {
                message: getErrorMessage(error),
              }),
        severity: 'error',
      })
    },
  })

  const lastNameMissing = lastName.trim() === ''

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (lastNameMissing) {
      return
    }
    // Beim Bearbeiten werden Leerwerte mitgesendet (PATCH leert das Feld),
    // beim Anlegen weggelassen. Der Account kann via PATCH nicht entfernt werden.
    const optional = (value: string) => (isEdit ? value.trim() : value.trim() || undefined)
    mutation.mutate({
      lastName: lastName.trim(),
      firstName: optional(firstName),
      email: optional(email),
      phone: optional(phone),
      accountId: selectedAccount?.id,
    })
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t(isEdit ? 'contacts.form.editTitle' : 'contacts.form.createTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label={t('contacts.form.lastName')}
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
              required
              autoFocus
              error={submitted && lastNameMissing}
              helperText={
                submitted && lastNameMissing ? t('contacts.form.lastNameRequired') : undefined
              }
            />
            <TextField
              label={t('contacts.form.firstName')}
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
            />
            <TextField
              label={t('contacts.form.email')}
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <TextField
              label={t('contacts.form.phone')}
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
            <Autocomplete
              options={options}
              value={selectedAccount}
              onChange={(_event, value) => {
                setAccountTouched(true)
                setSelectedAccount(value)
              }}
              inputValue={accountSearch}
              onInputChange={(_event, value) => setAccountSearch(value)}
              getOptionLabel={(option) => option.name}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              loading={optionsQuery.isFetching}
              noOptionsText={t('contacts.form.accountNoOptions')}
              loadingText={t('contacts.form.accountLoading')}
              renderInput={(params) => (
                <TextField {...params} label={t('contacts.form.account')} />
              )}
            />
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
