import { useCallback, useState } from 'react'
import { RequestError } from './api/client'

interface FormSubmitState {
  busy: boolean
  error: string | null
  fieldErrors: Record<string, string>
  submit: (run: () => Promise<unknown>, onSuccess?: () => void) => Promise<void>
  reset: () => void
}

/**
 * The three states every write in this application has: in flight, failed with
 * something to show the user, or done.
 *
 * The API returns one error shape for every failure, including per-field
 * validation messages, so the form can show the server's own words rather than
 * reimplementing the rules client-side and drifting from them. Client-side
 * `required` attributes catch the obvious cases early; the server remains the
 * authority, and this is how its answer reaches the field.
 */
export function useFormSubmit(): FormSubmitState {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const reset = useCallback(() => {
    setError(null)
    setFieldErrors({})
  }, [])

  const submit = useCallback(
    async (run: () => Promise<unknown>, onSuccess?: () => void) => {
      setBusy(true)
      setError(null)
      setFieldErrors({})
      try {
        await run()
        onSuccess?.()
      } catch (err) {
        if (err instanceof RequestError) {
          setError(err.message)
          setFieldErrors(err.fieldErrors)
        } else {
          setError('Could not reach the server.')
        }
      } finally {
        setBusy(false)
      }
    },
    [],
  )

  return { busy, error, fieldErrors, submit, reset }
}
