import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layout/AppLayout'
import { AccountsPage } from './pages/AccountsPage'
import { ContactsPage } from './pages/ContactsPage'
import { DashboardPage } from './pages/DashboardPage'
import { ImportWizardPage } from './pages/ImportWizardPage'
import { LeadsPage } from './pages/LeadsPage'
import { OpportunitiesPage } from './pages/OpportunitiesPage'
import { ProductsPage } from './pages/ProductsPage'
import { SettingsPage } from './pages/SettingsPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="leads" element={<LeadsPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="contacts" element={<ContactsPage />} />
        <Route path="opportunities" element={<OpportunitiesPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="import" element={<ImportWizardPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
