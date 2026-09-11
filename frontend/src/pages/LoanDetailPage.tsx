import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, RequestError } from '../api/client'
import type { Instalment, Loan, Repayment } from '../api/types'
import { useAsync } from '../useAsync'
import { taka, day } from '../format'

function instalmentPill(i: Instalment, today: string) {
  if (i.status === 'PAID') return <span className="pill pill-paid">Paid</span>
  if (i.dueOn < today) return <span className="pill pill-late">Overdue</span>
  if (i.status === 'PARTIAL') return <span className="pill pill-partial">Part paid</span>
  return <span className="pill pill-pending">Due</span>
}

export default function LoanDetailPage() {
  const { id } = useParams<{ id: string }>()
  const today = new Date().toISOString().slice(0, 10)

  const loan = useAsync<Loan>(() => api.get<Loan>(`/api/loans/${id}`), [id])
  const history = useAsync<Repayment[]>(
    () => api.get<Repayment[]>(`/api/loans/${id}/repayments`), [id])

  const [receiptNo, setReceiptNo] = useState('')
  const [amount, setAmount] = useState('')
  const [receivedOn, setReceivedOn] = useState(today)
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submitPayment(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    setBusy(true)
    try {
      await api.post(`/api/loans/${id}/repayments`, {
        receiptNo: receiptNo.trim(),
        amount,
        receivedOn,
      })
      setReceiptNo('')
      setAmount('')
      loan.reload()
      history.reload()
    } catch (err) {
      // The server's own message is shown rather than a generic one: it is the
      // side that knows whether this was an overpayment, a duplicate receipt or
      // a settled loan, and the clerk needs to be told which.
      setFormError(
        err instanceof RequestError ? err.message : 'Could not reach the server.')
    } finally {
      setBusy(false)
    }
  }

  if (loan.loading) return <p className="notice">Loading the loan...</p>
  if (loan.error) return <div className="error">{loan.error}</div>
  if (!loan.data) return null

  const l = loan.data
  const settled = l.status !== 'ACTIVE'

  return (
    <>
      <p className="sub" style={{ marginBottom: 6 }}>
        <Link to="/loans">Loans</Link> / {l.loanNumber}
      </p>
      <h1>{l.borrowerName}</h1>
      <p className="sub">
        {taka(l.principal)} over {l.termPeriods}{' '}
        {l.frequency === 'WEEKLY' ? 'weekly' : 'monthly'} instalments at{' '}
        {(Number(l.annualRate) * 100).toFixed(2)}% flat, disbursed{' '}
        {day(l.disbursedOn)}.
      </p>

      <div className="tiles">
        <div className="card tile">
          <div className="label">Total payable</div>
          <div className="value">{taka(l.totalDue)}</div>
        </div>
        <div className="card tile">
          <div className="label">Paid</div>
          <div className="value">{taka(l.totalPaid)}</div>
        </div>
        <div className="card tile">
          <div className="label">Outstanding</div>
          <div className="value">{taka(l.outstanding)}</div>
        </div>
        <div className="card tile">
          <div className="label">Overdue</div>
          <div className="value">{taka(l.overdue)}</div>
          <div className="note">
            {l.daysInArrears > 0 ? `${l.daysInArrears} days in arrears` : 'Not in arrears'}
          </div>
        </div>
      </div>

      <h2>Record a repayment</h2>
      <div className="card">
        {settled ? (
          <p style={{ margin: 0, color: 'var(--ink-3)' }}>
            This loan is {l.status === 'CLOSED' ? 'settled' : 'written off'}, so no
            further payment can be posted against its schedule.
          </p>
        ) : (
          <form className="row-actions" onSubmit={submitPayment}>
            <div className="field">
              <label htmlFor="receipt">Receipt number</label>
              <input id="receipt" required value={receiptNo}
                     onChange={(e) => setReceiptNo(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="amount">Amount</label>
              <input id="amount" required inputMode="decimal" value={amount}
                     onChange={(e) => setAmount(e.target.value)} placeholder="3000.00" />
            </div>
            <div className="field">
              <label htmlFor="on">Received on</label>
              <input id="on" type="date" required value={receivedOn}
                     onChange={(e) => setReceivedOn(e.target.value)} />
            </div>
            <button type="submit" disabled={busy}>
              {busy ? 'Posting...' : 'Post'}
            </button>
          </form>
        )}
        {formError && <div className="error" style={{ marginTop: 14, marginBottom: 0 }}>{formError}</div>}
      </div>

      <h2>Schedule</h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>No.</th>
              <th>Due</th>
              <th className="num">Principal</th>
              <th className="num">Service charge</th>
              <th className="num">Instalment</th>
              <th className="num">Paid</th>
              <th className="num">Balance</th>
              <th>State</th>
            </tr>
          </thead>
          <tbody>
            {(l.schedule ?? []).map((i) => (
              <tr key={i.instalmentNo}>
                <td>{i.instalmentNo}</td>
                <td>{day(i.dueOn)}</td>
                <td className="num">{taka(i.principalDue)}</td>
                <td className="num">{taka(i.interestDue)}</td>
                <td className="num">{taka(i.amountDue)}</td>
                <td className="num">{taka(i.amountPaid)}</td>
                <td className="num">{taka(i.balance)}</td>
                <td>{instalmentPill(i, today)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h2>Receipts</h2>
      {history.loading ? (
        <p className="notice">Loading receipts...</p>
      ) : !history.data || history.data.length === 0 ? (
        <div className="card notice">Nothing received yet.</div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Receipt</th>
                <th>Received</th>
                <th className="num">Amount</th>
                <th>Posted by</th>
              </tr>
            </thead>
            <tbody>
              {history.data.map((r) => (
                <tr key={r.id}>
                  <td>{r.receiptNo}</td>
                  <td>{day(r.receivedOn)}</td>
                  <td className="num">{taka(r.amount)}</td>
                  <td>{r.recordedBy ?? '\u2014'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
