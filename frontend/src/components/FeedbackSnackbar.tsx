import Alert from '@mui/material/Alert'
import Snackbar from '@mui/material/Snackbar'

export interface SnackbarState {
  message: string
  severity: 'success' | 'error'
}

interface FeedbackSnackbarProps {
  snackbar: SnackbarState | null
  onClose: () => void
}

/** Einheitliche Erfolgs-/Fehler-Snackbar für Mutations-Feedback. */
export function FeedbackSnackbar({ snackbar, onClose }: FeedbackSnackbarProps) {
  return (
    <Snackbar
      open={snackbar !== null}
      autoHideDuration={6000}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
    >
      {snackbar ? (
        <Alert severity={snackbar.severity} onClose={onClose} variant="filled">
          {snackbar.message}
        </Alert>
      ) : undefined}
    </Snackbar>
  )
}
