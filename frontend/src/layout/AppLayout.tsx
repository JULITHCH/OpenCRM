import AppBar from '@mui/material/AppBar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Drawer from '@mui/material/Drawer'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import BusinessIcon from '@mui/icons-material/Business'
import ContactsIcon from '@mui/icons-material/Contacts'
import DashboardIcon from '@mui/icons-material/Dashboard'
import Inventory2Icon from '@mui/icons-material/Inventory2'
import LogoutIcon from '@mui/icons-material/Logout'
import PersonSearchIcon from '@mui/icons-material/PersonSearch'
import SettingsIcon from '@mui/icons-material/Settings'
import TrendingUpIcon from '@mui/icons-material/TrendingUp'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/useAuth'
import { NotificationBell } from '../components/NotificationBell'

const drawerWidth = 240

const navItems = [
  { to: '/', labelKey: 'nav.dashboard', icon: <DashboardIcon /> },
  { to: '/leads', labelKey: 'nav.leads', icon: <PersonSearchIcon /> },
  { to: '/accounts', labelKey: 'nav.accounts', icon: <BusinessIcon /> },
  { to: '/contacts', labelKey: 'nav.contacts', icon: <ContactsIcon /> },
  { to: '/opportunities', labelKey: 'nav.opportunities', icon: <TrendingUpIcon /> },
  { to: '/products', labelKey: 'nav.products', icon: <Inventory2Icon /> },
  { to: '/import', labelKey: 'nav.import', icon: <UploadFileIcon /> },
  { to: '/settings', labelKey: 'nav.settings', icon: <SettingsIcon /> },
] as const

export function AppLayout() {
  const { t } = useTranslation()
  const { displayName, logout } = useAuth()
  const { pathname } = useLocation()

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" component="h1" noWrap sx={{ flexGrow: 1 }}>
            {t('app.title')}
          </Typography>
          <NotificationBell />
          <Typography variant="body2" sx={{ mr: 2 }}>
            {displayName}
          </Typography>
          <Button color="inherit" startIcon={<LogoutIcon />} onClick={logout}>
            {t('app.logout')}
          </Button>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: drawerWidth, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <Box component="nav" sx={{ overflow: 'auto' }}>
          <List>
            {navItems.map((item) => (
              <ListItem key={item.to} disablePadding>
                <ListItemButton
                  component={NavLink}
                  to={item.to}
                  selected={
                    item.to === '/' ? pathname === '/' : pathname.startsWith(item.to)
                  }
                >
                  <ListItemIcon>{item.icon}</ListItemIcon>
                  <ListItemText primary={t(item.labelKey)} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Box>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}
