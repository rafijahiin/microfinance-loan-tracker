import { useId, useState, type ReactNode } from 'react'

/**
 * A titled panel that opens and closes.
 *
 * `aria-controls` and `aria-expanded` are not decoration: without them a
 * screen reader announces a button with no indication that it reveals
 * anything, and no way to tell whether it already has.
 */
export function Disclosure({
  label,
  closeLabel,
  children,
}: {
  label: string
  closeLabel?: string
  children: (close: () => void) => ReactNode
}) {
  const [open, setOpen] = useState(false)
  const panelId = useId()

  return (
    <div style={{ marginBottom: 18 }}>
      <button
        type="button"
        className={open ? 'ghost' : ''}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((o) => !o)}
      >
        {open ? (closeLabel ?? 'Cancel') : label}
      </button>
      {open && (
        <div id={panelId} className="card" style={{ marginTop: 12 }}>
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  )
}
