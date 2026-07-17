import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import { useTranslation } from 'react-i18next'

interface ConfirmDialogProps {
  open: boolean
  title: string
  body: string
  confirmLabel: string
  pending?: boolean
  onConfirm: () => void
  onClose: () => void
}

/** Bestätigungs-Dialog für destruktive Aktionen (z. B. Löschen). */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  pending = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const { t } = useTranslation()

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{body}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button color="error" variant="contained" onClick={onConfirm} disabled={pending}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
