import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layout/AppLayout'
import { AccountsPage } from './pages/AccountsPage'
import { ContactsPage } from './pages/ContactsPage'
import { ImportWizardPage } from './pages/ImportWizardPage'
import { LeadsPage } from './pages/LeadsPage'
import { PlaceholderPage } from './pages/PlaceholderPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<PlaceholderPage titleKey="nav.dashboard" />} />
        <Route path="leads" element={<LeadsPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="contacts" element={<ContactsPage />} />
        <Route path="import" element={<ImportWizardPage />} />
        <Route path="settings" element={<PlaceholderPage titleKey="nav.settings" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
