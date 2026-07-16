import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layout/AppLayout'
import { LeadsPage } from './pages/LeadsPage'
import { PlaceholderPage } from './pages/PlaceholderPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<PlaceholderPage titleKey="nav.dashboard" />} />
        <Route path="leads" element={<LeadsPage />} />
        <Route path="accounts" element={<PlaceholderPage titleKey="nav.accounts" />} />
        <Route path="contacts" element={<PlaceholderPage titleKey="nav.contacts" />} />
        <Route path="settings" element={<PlaceholderPage titleKey="nav.settings" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
