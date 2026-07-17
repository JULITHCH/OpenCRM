import { useMemo, useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createProduct, listProducts, updateProduct, type Product } from '../api/products'
import { ApiError, getErrorMessage } from '../api/client'
import { FeedbackSnackbar, type SnackbarState } from '../components/FeedbackSnackbar'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useFormatters } from '../hooks/useFormatters'

const productsQueryKey = ['products'] as const

export function ProductsPage() {
  const { t } = useTranslation()
  const { formatCurrency } = useFormatters()
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim(), 300)
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)
  // null = Dialog zu, { product: null } = Anlage, { product } = Bearbeitung.
  const [editor, setEditor] = useState<{ product: Product | null } | null>(null)

  const productsQuery = useInfiniteQuery({
    queryKey: [...productsQueryKey, 'list', q],
    queryFn: ({ pageParam, signal }) =>
      listProducts({ q: q || undefined, cursor: pageParam }, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
  const products = useMemo(
    () => productsQuery.data?.pages.flatMap((page) => page.items) ?? [],
    [productsQuery.data],
  )

  return (
    <>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h5" component="h2">
          {t('products.heading')}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setEditor({ product: null })}
        >
          {t('products.create')}
        </Button>
      </Stack>

      <TextField
        label={t('common.search')}
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        size="small"
        sx={{ mb: 2, width: 320 }}
      />

      {productsQuery.isPending && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress aria-label={t('products.loading')} />
        </Box>
      )}
      {productsQuery.isError && (
        <Alert severity="error">
          {t('products.loadError', { message: getErrorMessage(productsQuery.error) })}
        </Alert>
      )}
      {productsQuery.isSuccess && (
        <>
          <TableContainer component={Paper}>
            <Table size="small" aria-label={t('products.heading')}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('products.columns.sku')}</TableCell>
                  <TableCell>{t('products.columns.name')}</TableCell>
                  <TableCell>{t('products.columns.category')}</TableCell>
                  <TableCell align="right">{t('products.columns.listPrice')}</TableCell>
                  <TableCell>{t('products.columns.active')}</TableCell>
                  <TableCell align="right">{t('common.actions')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {products.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      <Typography color="text.secondary" py={2}>
                        {t('products.empty')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  products.map((product) => (
                    <TableRow key={product.id} hover>
                      <TableCell>{product.sku}</TableCell>
                      <TableCell>{product.name}</TableCell>
                      <TableCell>{product.category || '—'}</TableCell>
                      <TableCell align="right">
                        {formatCurrency(product.listPrice, product.currency)}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          color={product.active ? 'success' : 'default'}
                          label={t(product.active ? 'products.state.active' : 'products.state.inactive')}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <IconButton
                          size="small"
                          aria-label={t('products.rowActions.edit')}
                          onClick={() => setEditor({ product })}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
          {productsQuery.hasNextPage && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
              <Button
                onClick={() => productsQuery.fetchNextPage()}
                disabled={productsQuery.isFetchingNextPage}
              >
                {t('common.loadMore')}
              </Button>
            </Box>
          )}
        </>
      )}

      {editor !== null && (
        <ProductDialog
          product={editor.product}
          onClose={() => setEditor(null)}
          onNotify={setSnackbar}
        />
      )}

      <FeedbackSnackbar snackbar={snackbar} onClose={() => setSnackbar(null)} />
    </>
  )
}

interface ProductDialogProps {
  /** null = neues Produkt anlegen, sonst bestehendes bearbeiten. */
  product: Product | null
  onClose: () => void
  onNotify: (snackbar: SnackbarState) => void
}

function ProductDialog({ product, onClose, onNotify }: ProductDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const isEdit = product !== null
  const [sku, setSku] = useState(product?.sku ?? '')
  const [name, setName] = useState(product?.name ?? '')
  const [category, setCategory] = useState(product?.category ?? '')
  const [listPrice, setListPrice] = useState(product !== null ? String(product.listPrice) : '')
  const [active, setActive] = useState(product?.active ?? true)
  const [submitted, setSubmitted] = useState(false)

  const mutation = useMutation({
    mutationFn: async () => {
      const price = Number(listPrice)
      if (isEdit) {
        // Beim Bearbeiten leert eine leere Kategorie das Feld (PATCH sendet '').
        return updateProduct(product.id, {
          sku: sku.trim(),
          name: name.trim(),
          listPrice: price,
          category: category.trim(),
          active,
        })
      }
      const created = await createProduct({
        sku: sku.trim(),
        name: name.trim(),
        listPrice: price,
        category: category.trim() || undefined,
      })
      // Die Anlage kennt kein active-Flag — ein abgewähltes „Aktiv“ direkt nachziehen.
      if (!active) {
        return updateProduct(created.id, { active: false })
      }
      return created
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: productsQueryKey })
      onNotify({
        message: t(isEdit ? 'products.updateSuccess' : 'products.createSuccess'),
        severity: 'success',
      })
      onClose()
    },
    onError: (error) => {
      onNotify({
        message:
          error instanceof ApiError && error.status === 403
            ? t('common.forbidden')
            : t(isEdit ? 'products.updateError' : 'products.createError', {
                message: getErrorMessage(error),
              }),
        severity: 'error',
      })
    },
  })

  const skuMissing = sku.trim() === ''
  const nameMissing = name.trim() === ''
  const priceInvalid =
    listPrice.trim() === '' || !Number.isFinite(Number(listPrice)) || Number(listPrice) < 0

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitted(true)
    if (skuMissing || nameMissing || priceInvalid) {
      return
    }
    mutation.mutate()
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t(isEdit ? 'products.form.editTitle' : 'products.form.createTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label={t('products.form.sku')}
              value={sku}
              onChange={(event) => setSku(event.target.value)}
              required
              autoFocus
              error={submitted && skuMissing}
              helperText={submitted && skuMissing ? t('products.form.skuRequired') : undefined}
            />
            <TextField
              label={t('products.form.name')}
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
              error={submitted && nameMissing}
              helperText={submitted && nameMissing ? t('products.form.nameRequired') : undefined}
            />
            <TextField
              label={t('products.form.category')}
              value={category}
              onChange={(event) => setCategory(event.target.value)}
            />
            <TextField
              label={t('products.form.listPrice')}
              type="number"
              value={listPrice}
              onChange={(event) => setListPrice(event.target.value)}
              required
              inputProps={{ min: 0, step: '0.01' }}
              error={submitted && priceInvalid}
              helperText={
                submitted && priceInvalid ? t('products.form.listPriceRequired') : undefined
              }
            />
            <FormControlLabel
              control={
                <Switch checked={active} onChange={(event) => setActive(event.target.checked)} />
              }
              label={t('products.form.active')}
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
