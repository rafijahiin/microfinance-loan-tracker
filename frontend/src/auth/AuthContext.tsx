import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
  type ReactNode,
} from 'react'
import { api, setToken, getToken, setUnauthorizedHandler } from '../api/client'
import type { LoginResponse, Role } from '../api/types'

interface Session {
  email: string
  role: Role
  partnerId: number | null
}

interface AuthState {
  session: Session | null
  ready: boolean
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

const SESSION_KEY = 'loan-tracker.session'

function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [ready, setReady] = useState(false)

  const signOut = useCallback(() => {
    setToken(null)
    try {
      localStorage.removeItem(SESSION_KEY)
    } catch {
      /* ignore */
    }
    setSession(null)
  }, [])

  // Restore a session on first paint. Without the `ready` flag the router
  // would render the login page for one frame before the stored token is
  // read, which flashes a sign-in form at an already-signed-in user.
  useEffect(() => {
    if (getToken()) setSession(readStoredSession())
    setReady(true)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(signOut)
    return () => setUnauthorizedHandler(null)
  }, [signOut])

  const signIn = useCallback(async (email: string, password: string) => {
    const res = await api.post<LoginResponse>('/api/auth/login', { email, password })
    setToken(res.token)
    const next: Session = { email: res.email, role: res.role, partnerId: res.partnerId }
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(next))
    } catch {
      /* ignore */
    }
    setSession(next)
  }, [])

  const value = useMemo(
    () => ({ session, ready, signIn, signOut }),
    [session, ready, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside an AuthProvider')
  return ctx
}
