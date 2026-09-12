import type { AuditEvent } from '../api/types'
import { taka, when } from '../format'

const TONE: Record<AuditEvent['action'], string> = {
  MEMBER_ENROLLED: 'pill-pending',
  LOAN_DISBURSED: 'pill-paid',
  REPAYMENT_POSTED: 'pill-paid',
  LOAN_WRITTEN_OFF: 'pill-late',
}

/**
 * The append-only trail, rendered.
 *
 * Deliberately shows the actor on every row rather than tucking it behind a
 * hover or a detail view. "Who posted this" is the whole reason the table
 * exists, and a column nobody sees answers nothing.
 */
export function ActivityFeed({
  events,
  emptyMessage = 'Nothing recorded yet.',
}: {
  events: AuditEvent[]
  emptyMessage?: string
}) {
  if (events.length === 0) {
    return <div className="card notice">{emptyMessage}</div>
  }

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>When</th>
            <th>Action</th>
            <th>What happened</th>
            <th className="num">Amount</th>
            <th>By</th>
          </tr>
        </thead>
        <tbody>
          {events.map((e) => (
            <tr key={e.id}>
              <td style={{ whiteSpace: 'nowrap' }}>{when(e.occurredAt)}</td>
              <td>
                <span className={`pill ${TONE[e.action] ?? 'pill-pending'}`}>
                  {e.actionLabel}
                </span>
              </td>
              <td>{e.summary}</td>
              <td className="num">{e.amount ? taka(e.amount) : '\u2014'}</td>
              <td style={{ fontSize: 12.5, color: 'var(--ink-2)' }}>
                {e.actorEmail}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
