import { useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { Borrower, Loan, Page, RepaymentFrequency } from '../api/types'
import { useAsync } from '../useAsync'
import { useFormSubmit } from '../useFormSubmit'
import { Field } from './Field'

const today = () => new Date().toISOString().slice(0, 10)

export function DisburseLoanForm({ onDisbursed }: { onDisbursed: () => void }) {
  const members = useAsync<Page<Borrower>>(
    () => api.get<Page<Borrower>>('/api/borrowers?size=200'), [])

  const [borrowerId, setBorrowerId] = useState('')
  const [loanNumber, setLoanNumber] = useState('')
  const [principal, setPrincipal] = useState('')
  const [ratePercent, setRatePercent] = useState('12')
  const [termPeriods, setTermPeriods] = useState('40')
  const [frequency, setFrequency] = useState<RepaymentFrequency>('WEEKLY')
  const [disbursedOn, setDisbursedOn] = useState(today())

  const { busy, error, fieldErrors, submit } = useFormSubmit()

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void submit(
      () =>
        api.post<Loan>('/api/loans', {
          borrowerId: Number(borrowerId),
          loanNumber: loanNumber.trim(),
          principal,
          // The clerk types 12 because that is how a rate is spoken. The API
          // takes a fraction and rejects anything above 1, so the conversion
          // happens here, once, rather than being a trap for whoever types it.
          annualRate: (Number(ratePercent) / 100).toFixed(4),
          termPeriods: Number(termPeriods),
          frequency,
          disbursedOn,
        }),
      onDisbursed,
    )
  }

  const options = members.data?.content ?? []

  return (
    <form onSubmit={onSubmit} aria-label="Disburse a loan">
      {error && <div className="error" role="alert">{error}</div>}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
        <Field id="borrower" label="Member" error={fieldErrors.borrowerId}>
          {(p) => (
            <select {...p} required value={borrowerId}
                    onChange={(e) => setBorrowerId(e.target.value)}>
              <option value="">Select…</option>
              {options.map((b) => (
                <option key={b.id} value={b.id}>{b.memberCode} · {b.name}</option>
              ))}
            </select>
          )}
        </Field>

        <Field id="loanNumber" label="Loan number" error={fieldErrors.loanNumber}>
          {(p) => (
            <input {...p} required value={loanNumber}
                   onChange={(e) => setLoanNumber(e.target.value)} />
          )}
        </Field>

        <Field id="principal" label="Principal (BDT)" error={fieldErrors.principal}>
          {(p) => (
            <input {...p} required inputMode="decimal" value={principal}
                   onChange={(e) => setPrincipal(e.target.value)} />
          )}
        </Field>

        <Field id="ratePercent" label="Service charge (% per year, flat)"
               error={fieldErrors.annualRate}
               hint="Charged on the original principal for the whole term">
          {(p) => (
            <input {...p} required inputMode="decimal" value={ratePercent}
                   onChange={(e) => setRatePercent(e.target.value)} />
          )}
        </Field>

        <Field id="frequency" label="Repayment frequency" error={fieldErrors.frequency}>
          {(p) => (
            <select {...p} required value={frequency}
                    onChange={(e) => setFrequency(e.target.value as RepaymentFrequency)}>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          )}
        </Field>

        <Field id="termPeriods" label="Number of instalments"
               error={fieldErrors.termPeriods}
               hint={frequency === 'WEEKLY' ? 'Weeks' : 'Months'}>
          {(p) => (
            <input {...p} required inputMode="numeric" value={termPeriods}
                   onChange={(e) => setTermPeriods(e.target.value)} />
          )}
        </Field>

        <Field id="disbursedOn" label="Disbursed on" error={fieldErrors.disbursedOn}>
          {(p) => (
            <input {...p} required type="date" value={disbursedOn}
                   onChange={(e) => setDisbursedOn(e.target.value)} />
          )}
        </Field>
      </div>

      <button type="submit" disabled={busy}>
        {busy ? 'Disbursing…' : 'Disburse loan'}
      </button>
    </form>
  )
}
