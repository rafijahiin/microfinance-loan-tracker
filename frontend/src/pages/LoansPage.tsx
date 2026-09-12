import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { Disclosure } from '../components/Disclosure'
import { DisburseLoanForm } from '../components/DisburseLoanForm'
import type { Loan, Page } from '../api/types'
import { useAsync } from '../useAsync'
import { taka, day } from '../format'

function statusPill(loan: Loan) {
  if (loan.status === 'CLOSED') return <span className="pill pill-paid">Settled</span>
  if (loan.status === 'WRITTEN_OFF') return <span className="pill pill-late">Written off</span>
  if (loan.daysInArrears > 30) {
    return <span className="pill pill-late">{loan.daysInArrears} days late</span>
  }
  if (loan.daysInArrears > 0) {
    return <span className="pill pill-partial">{loan.daysInArrears} days late</span>
  }
  return <span className="pill pill-pending">Current</span>
}

export default function LoansPage() {
  const { data, error, loading, reload } = useAsync<Page<Loan>>(
    () => api.get<Page<Loan>>('/api/loans?size=50'),
    [],
  )

  if (loading) return <p className="notice">Loading loans...</p>
  if (error) return <div className="error">{error}</div>
  if (!data) return null

  return (
    <>
      <h1>Loans</h1>
      <p className="sub">{data.totalElements.toLocaleString('en-IN')} in your portfolio.</p>

      <Disclosure label="Disburse a loan">
        {(close) => (
          <DisburseLoanForm
            onDisbursed={() => {
              close()
              reload()
            }}
          />
        )}
      </Disclosure>

      {data.content.length === 0 ? (
        <div className="card notice">No loans yet.</div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Loan</th>
                <th>Member</th>
                <th>Product</th>
                <th>Disbursed</th>
                <th className="num">Principal</th>
                <th className="num">Outstanding</th>
                <th className="num">Overdue</th>
                <th>State</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((loan) => (
                <tr key={loan.id}>
                  <td><Link to={`/loans/${loan.id}`}>{loan.loanNumber}</Link></td>
                  <td>{loan.borrowerName}</td>
                  {/* One text node, not three. "40" and "weekly" as separate
                      nodes read identically on screen but are two unrelated
                      strings to anything reading the DOM, tests included. */}
                  <td>{`${loan.termPeriods} ${
                    loan.frequency === 'WEEKLY' ? 'weekly' : 'monthly'
                  }`}</td>
                  <td>{day(loan.disbursedOn)}</td>
                  <td className="num">{taka(loan.principal)}</td>
                  <td className="num">{taka(loan.outstanding)}</td>
                  <td className="num">
                    {Number(loan.overdue) > 0 ? taka(loan.overdue) : '\u2014'}
                  </td>
                  <td>{statusPill(loan)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
