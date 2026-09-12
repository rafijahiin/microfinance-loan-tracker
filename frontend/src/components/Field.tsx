import type { ReactNode } from 'react'

/** One labelled input with the server's validation message beneath it.
 *
 *  aria-describedby ties the message to the input so a screen reader reads it
 *  when focus lands there, and aria-invalid says something is wrong without
 *  relying on the red text being seen. */
export function Field({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string
  label: string
  error?: string
  hint?: string
  children: (props: {
    id: string
    'aria-invalid': boolean | undefined
    'aria-describedby': string | undefined
  }) => ReactNode
}) {
  const messageId = error ? `${id}-error` : hint ? `${id}-hint` : undefined

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children({
        id,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': messageId,
      })}
      {error && (
        <div id={messageId} role="alert"
             style={{ color: 'var(--danger)', fontSize: 12, marginTop: 4 }}>
          {error}
        </div>
      )}
      {!error && hint && (
        <div id={messageId} style={{ color: 'var(--ink-3)', fontSize: 11.5, marginTop: 4 }}>
          {hint}
        </div>
      )}
    </div>
  )
}
