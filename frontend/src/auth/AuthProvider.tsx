import { useEffect, useState, type ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useTranslation } from 'react-i18next'
import { keycloak } from './keycloak'
import { AuthContext, type AuthContextValue } from './AuthContext'

/** Profil-Claims, die der Realm "opencrm" in Access-/ID-Token schreibt. */
interface ProfileClaims {
  name?: string
  preferred_username?: string
}

// keycloak.init() darf nur einmal aufgerufen werden — der Guard liegt auf Modulebene,
// damit der doppelte Effect-Lauf des React-StrictMode (Dev) keinen zweiten Init auslöst.
let initStarted = false

export function AuthProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [initialized, setInitialized] = useState(false)
  const [initError, setInitError] = useState(false)

  useEffect(() => {
    if (initStarted) {
      return
    }
    initStarted = true

    // Automatischer Silent-Refresh: kurz vor Ablauf (bzw. bei Ablauf) wird das Token
    // erneuert; schlägt das fehl (Session abgelaufen), zurück zum Login.
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => keycloak.login())
    }

    keycloak
      .init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then(() => setInitialized(true))
      .catch(() => setInitError(true))
  }, [])

  if (!initialized) {
    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 2,
        }}
      >
        {initError ? (
          <Alert severity="error">{t('app.authError')}</Alert>
        ) : (
          <>
            <CircularProgress />
            <Typography variant="body1" color="text.secondary">
              {t('app.authLoading')}
            </Typography>
          </>
        )}
      </Box>
    )
  }

  const claims = (keycloak.tokenParsed ?? {}) as ProfileClaims
  const value: AuthContextValue = {
    keycloak,
    displayName: claims.name ?? claims.preferred_username ?? '',
    logout: () => {
      void keycloak.logout({ redirectUri: window.location.origin })
    },
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
