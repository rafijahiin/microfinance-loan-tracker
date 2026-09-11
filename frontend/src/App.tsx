import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import BorrowersPage from './pages/BorrowersPage'
import LoansPage from './pages/LoansPage'
import LoanDetailPage from './pages/LoanDetailPage'

function Chrome({ children }: { children: React.ReactNode }) {
  const { session, signOut } = useAuth()
  return (
    <>
      <header className="bar">
        <div className="inner">
          <span className="brand">
            <span className="mark">LT</span>
            <span className="name">
              Loan Portfolio Tracker
              <small>Two-tier microfinance</small>
            </span>
          </span>
          <nav>
            <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
              Portfolio
            </NavLink>
            <NavLink to="/loans" className={({ isActive }) => (isActive ? 'active' : '')}>
              Loans
            </NavLink>
            <NavLink to="/borrowers" className={({ isActive }) => (isActive ? 'active' : '')}>
              Members
            </NavLink>
          </nav>
          <span className="who">
            {session?.email} ({session?.role === 'ADMIN' ? 'Administrator' : 'Partner officer'})
            {'  '}
            <button className="ghost" style={{ marginLeft: 10 }} onClick={signOut}>
              Sign out
            </button>
          </span>
        </div>
      </header>
      <div className="shell">
        {children}
        <p className="colophon">
          An independent portfolio project. Not affiliated with, endorsed by, or
          produced for Palli Karma-Sahayak Foundation. The palette and
          typefaces follow PKSF&rsquo;s published visual identity because the
          domain is theirs; the emblem and the organisation&rsquo;s name are
          deliberately not used.
        </p>
      </div>
    </>
  )
}

export default function App() {
  const { session, ready } = useAuth()

  // Hold the first paint until the stored token has been read, otherwise a
  // signed-in user sees the login form flash before being redirected.
  if (!ready) return null

  if (!session) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Chrome>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/loans" element={<LoansPage />} />
        <Route path="/loans/:id" element={<LoanDetailPage />} />
        <Route path="/borrowers" element={<BorrowersPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Chrome>
  )
}
