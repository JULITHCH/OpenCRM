import { useState } from 'react'
import Badge from '@mui/material/Badge'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import ListItemText from '@mui/material/ListItemText'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Typography from '@mui/material/Typography'
import NotificationsIcon from '@mui/icons-material/Notifications'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type AppNotification,
} from '../api/notifications'
import { getErrorMessage } from '../api/client'
import { useFormatters } from '../hooks/useFormatters'
import { FeedbackSnackbar, type SnackbarState } from './FeedbackSnackbar'

const notificationsQueryKey = ['notifications'] as const

/** Anzeigetext je Benachrichtigungstyp; unbekannte Typen zeigen den Typ selbst. */
function notificationText(t: TFunction, notification: AppNotification): string {
  if (notification.type === 'LEAD_SLA_BREACH') {
    const title = notification.payload['title']
    return t('notifications.leadSlaBreach', { title: typeof title === 'string' ? title : '' })
  }
  return notification.type
}

/** Glocke in der AppBar: Badge mit Ungelesenen (60-s-Polling) und Menü zum Gelesen-Markieren. */
export function NotificationBell() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { formatDateTime } = useFormatters()
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null)

  const notificationsQuery = useQuery({
    queryKey: [...notificationsQueryKey, 'unread'],
    queryFn: ({ signal }) => listNotifications(true, signal),
    refetchInterval: 60_000,
  })
  const notifications = notificationsQuery.data ?? []

  const invalidate = () => queryClient.invalidateQueries({ queryKey: notificationsQueryKey })
  const onError = (error: unknown) => {
    setSnackbar({
      message: t('notifications.actionError', { message: getErrorMessage(error) }),
      severity: 'error',
    })
  }

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: invalidate,
    onError,
  })
  const markAllReadMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: async () => {
      await invalidate()
      setAnchorEl(null)
    },
    onError,
  })

  return (
    <>
      <IconButton
        color="inherit"
        aria-label={t('notifications.title')}
        onClick={(event) => setAnchorEl(event.currentTarget)}
        sx={{ mr: 1 }}
      >
        <Badge badgeContent={notifications.length} color="error">
          <NotificationsIcon />
        </Badge>
      </IconButton>
      <Menu
        anchorEl={anchorEl}
        open={anchorEl !== null}
        onClose={() => setAnchorEl(null)}
        slotProps={{ paper: { sx: { width: 360 } } }}
      >
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="subtitle1">{t('notifications.title')}</Typography>
        </Box>
        <Divider />
        {notifications.length === 0 ? (
          <Box sx={{ px: 2, py: 2 }}>
            <Typography variant="body2" color="text.secondary">
              {t('notifications.empty')}
            </Typography>
          </Box>
        ) : (
          notifications.map((notification) => (
            <MenuItem
              key={notification.id}
              onClick={() => markReadMutation.mutate(notification.id)}
              disabled={markReadMutation.isPending}
            >
              <ListItemText
                primary={notificationText(t, notification)}
                secondary={formatDateTime(notification.createdAt)}
                primaryTypographyProps={{ sx: { whiteSpace: 'normal' } }}
              />
            </MenuItem>
          ))
        )}
        <Divider />
        <Box sx={{ px: 1, py: 0.5, display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            size="small"
            onClick={() => markAllReadMutation.mutate()}
            disabled={notifications.length === 0 || markAllReadMutation.isPending}
          >
            {t('notifications.markAllRead')}
          </Button>
        </Box>
      </Menu>
      <FeedbackSnackbar snackbar={snackbar} onClose={() => setSnackbar(null)} />
    </>
  )
}
