import { api } from '../api/client'
import type { AuditEvent, Page, PortfolioSummary } from '../api/types'
import { useAsync } from '../useAsync'
import { taka, percent, day } from '../format'
import { ActivityFeed } from '../components/ActivityFeed'

export default function DashboardPage() {
  const { data, error, loading } = useAsync<PortfolioSummary>(
    () => api.get<PortfolioSummary>('/api/portfolio/summary'),
    [],
  )
  const activity = useAsync<Page<AuditEvent>>(
    () => api.get<Page<AuditEvent>>('/api/audit?size=15'),
    [],
  )

  if (loading) return <p className="notice">Loading the portfolio...</p>
  if (error) return <div className="error">{error}</div>
  if (!data) return null

  const par = Number(data.par30)

  return (
    <>
      <h1>Portfolio</h1>
      <p className="sub">As at {day(data.asOf)}.</p>

      <div className="tiles">
        <div className="card tile">
          <div className="label">Active loans</div>
          <div className="value">{data.activeLoans.toLocaleString('en-IN')}</div>
          <div className="note">{data.loansInArrears} in arrears</div>
        </div>
        <div className="card tile">
          <div className="label">Outstanding</div>
          <div className="value">{taka(data.outstanding)}</div>
          <div className="note">Principal and service charge still owed</div>
        </div>
        <div className="card tile">
          <div className="label">Overdue</div>
          <div className="value">{taka(data.overdue)}</div>
          <div className="note">Past due date and unpaid</div>
        </div>
        <div className="card tile">
          <div className="label">PAR 30</div>
          <div className="value">{percent(data.par30)}</div>
          <div className="note">
            {par > 0.05 ? 'Above the 5% threshold' : 'Within the usual threshold'}
          </div>
        </div>
      </div>

      <h2>Recent activity</h2>
      <p className="sub" style={{ marginBottom: 12 }}>
        Who did what, in order. The trail is written in the same transaction as
        the change it describes, so a refused payment leaves no entry claiming
        it went through.
      </p>
      {activity.error ? (
        <div className="error">{activity.error}</div>
      ) : activity.loading ? (
        <p className="notice">Loading activity\u2026</p>
      ) : (
        <ActivityFeed events={activity.data?.content ?? []} />
      )}

      <h2>How PAR 30 is counted</h2>
      <div className="card" style={{ color: 'var(--ink-2)', lineHeight: 1.65 }}>
        Portfolio at Risk over 30 days is the <strong>entire outstanding balance</strong>{' '}
        of every loan carrying an instalment more than thirty days late, divided by
        the total outstanding. The whole balance counts, not only the instalments
        already missed: a borrower a month behind puts the rest of the loan at risk
        too. Written-off loans are excluded, because the loss on them has already
        been recognised and leaving them in would let a write-off improve the ratio.
      </div>
    </>
  )
}
