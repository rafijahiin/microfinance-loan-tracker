import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ActivityFeed } from './ActivityFeed'
import type { AuditEvent } from '../api/types'

function event(over: Partial<AuditEvent>): AuditEvent {
  return {
    id: 1,
    occurredAt: '2026-09-12T09:30:00Z',
    action: 'REPAYMENT_POSTED',
    actionLabel: 'Repayment posted',
    actorEmail: 'officer.rangpur@example.org',
    actorRole: 'PO_OFFICER',
    entityType: 'LOAN',
    entityId: 4,
    summary: 'Receipt R-001: 3000.00 posted against L-0001, leaving 9000.00 outstanding',
    amount: '3000.00',
    ...over,
  }
}

describe('ActivityFeed', () => {
  it('shows who did it on every row', () => {
    // The actor is the whole reason the table exists. Tucking it behind a
    // hover or a detail view would answer nothing.
    render(<ActivityFeed events={[event({})]} />)

    expect(screen.getByText('officer.rangpur@example.org')).toBeInTheDocument()
  })

  it('reads without knowing the schema', () => {
    render(<ActivityFeed events={[event({})]} />)

    expect(screen.getByText(/Receipt R-001/)).toBeInTheDocument()
    expect(screen.getByText('Repayment posted')).toBeInTheDocument()
  })

  it('formats the amount as money and the time to the minute', () => {
    render(<ActivityFeed events={[event({})]} />)

    expect(screen.getByText('3,000.00')).toBeInTheDocument()
    // Seconds are noise on a feed; the date is what someone reconciling a
    // receipt is looking for.
    expect(screen.getByText(/12\/09\/2026 \d{2}:\d{2}/)).toBeInTheDocument()
  })

  it('shows a dash where an action carries no money', () => {
    render(<ActivityFeed events={[event({
      action: 'MEMBER_ENROLLED',
      actionLabel: 'Member enrolled',
      amount: null,
      summary: 'Rahima Begum enrolled as M-0001 (national ID *********6789)',
    })]} />)

    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('never renders a raw national ID, because the API never sends one', () => {
    const summary = 'Rahima Begum enrolled as M-0001 (national ID *********6789)'
    render(<ActivityFeed events={[event({
      action: 'MEMBER_ENROLLED', actionLabel: 'Member enrolled',
      amount: null, summary,
    })]} />)

    expect(screen.getByText(summary)).toBeInTheDocument()
    expect(screen.queryByText(/1990123456789/)).not.toBeInTheDocument()
  })

  it('marks a write-off differently from a repayment', () => {
    // Both move money, but one is a recovery and one is a loss. They should
    // not look alike in a list someone scans.
    const { container } = render(
      <ActivityFeed events={[
        event({ id: 1 }),
        event({ id: 2, action: 'LOAN_WRITTEN_OFF', actionLabel: 'Loan written off' }),
      ]} />,
    )

    expect(container.querySelector('.pill-paid')).toBeInTheDocument()
    expect(container.querySelector('.pill-late')).toBeInTheDocument()
  })

  it('says so plainly when nothing has happened', () => {
    render(<ActivityFeed events={[]} />)

    expect(screen.getByText('Nothing recorded yet.')).toBeInTheDocument()
  })

  it('accepts a caller-supplied empty message, for a single loan', () => {
    render(<ActivityFeed events={[]}
                         emptyMessage="Nothing recorded against this loan yet." />)

    expect(screen.getByText('Nothing recorded against this loan yet.'))
      .toBeInTheDocument()
  })

  it('keeps the order it is given rather than re-sorting', () => {
    // The API returns newest first. Re-sorting here would mean two places
    // deciding the order, and they would eventually disagree.
    render(<ActivityFeed events={[
      event({ id: 1, summary: 'newest' }),
      event({ id: 2, summary: 'middle' }),
      event({ id: 3, summary: 'oldest' }),
    ]} />)

    const rows = screen.getAllByRole('row').slice(1)
    expect(rows[0]).toHaveTextContent('newest')
    expect(rows[2]).toHaveTextContent('oldest')
  })
})
