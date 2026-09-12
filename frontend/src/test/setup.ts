import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

// jsdom in this configuration exposes no Storage API at all: `typeof
// localStorage` is "undefined", not a stub that throws. That is a real
// environment the application has to cope with (private browsing and blocked
// site data behave similarly), and the client's try/catch around storage is
// written for it. Tests that exercise the signed-in path still need somewhere
// to keep a token, so a minimal in-memory implementation stands in.
//
// The graceful-degradation path is not left untested because of this: see
// "survives an environment with no storage at all" in client.test.ts, which
// removes it again deliberately.
if (typeof globalThis.localStorage === 'undefined') {
  const store = new Map<string, string>()
  const memoryStorage: Storage = {
    get length() {
      return store.size
    },
    clear: () => store.clear(),
    getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
    key: (i: number) => Array.from(store.keys())[i] ?? null,
    removeItem: (k: string) => void store.delete(k),
    setItem: (k: string, v: string) => void store.set(k, String(v)),
  }
  Object.defineProperty(globalThis, 'localStorage', {
    value: memoryStorage,
    configurable: true,
    writable: true,
  })
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  try {
    localStorage.clear()
  } catch {
    /* ignore */
  }
})
