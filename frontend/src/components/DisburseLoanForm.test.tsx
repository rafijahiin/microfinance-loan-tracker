import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DisburseLoanForm } from './DisburseLoanForm'
import { stubFetch, lastBody } from '../test/fetchMock'

const MEMBERS = {
  content: [
    { id: 3, partnerId: 7, partnerCode: 'PO-001', memberCode: 'M-0001',
      name: 'Rahima Begum', nationalIdMasked: '*********6789', phone: null,
      village: null, union: null, upazila: null, district: 'Rangpur',
      enrolledOn: '2026-01-01' },
  ],
  totalElements: 1, totalPages: 1, number: 0, size: 20,
}

function routes(post?: { status: number; body: unknown }) {
  return (url: string, init?: RequestInit) => {
    if (url.includes('/api/borrowers')) return { body: MEMBERS }
    if (url.includes('/api/loans') && init?.method === 'POST') {
      return post ?? { status: 201, body: { id: 1 } }
    }
    return undefined
  }
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(await screen.findByLabelText('Member'), '3')
  await user.type(screen.getByLabelText('Loan number'), 'L-2026-0001')
  await user.type(screen.getByLabelText('Principal (BDT)'), '30000')
}

describe('DisburseLoanForm', () => {
  it('sends the rate as a fraction although the clerk types a percentage',
    async () => {
      // This is the trap the API's 1.0000 cap exists to close. A clerk says
      // "twelve per cent" and types 12; the API takes 0.12 and rejects 12
      // outright. The conversion belongs here, once, not in the clerk's head.
      const user = userEvent.setup()
      const { calls } = stubFetch(routes())

      render(<DisburseLoanForm onDisbursed={() => {}} />)
      await fillValidForm(user)
      await user.clear(screen.getByLabelText(/Service charge/))
      await user.type(screen.getByLabelText(/Service charge/), '12')
      await user.click(screen.getByRole('button', { name: 'Disburse loan' }))

      await waitFor(() => expect(lastBody(calls).annualRate).toBe('0.1200'))
    })

  it('handles a fractional percentage without losing precision', async () => {
    // 13.75 per cent is an ordinary quote here, and rounding it to 0.14 would
    // change every instalment.
    const user = userEvent.setup()
    const { calls } = stubFetch(routes())

    render(<DisburseLoanForm onDisbursed={() => {}} />)
    await fillValidForm(user)
    await user.clear(screen.getByLabelText(/Service charge/))
    await user.type(screen.getByLabelText(/Service charge/), '13.75')
    await user.click(screen.getByRole('button', { name: 'Disburse loan' }))

    await waitFor(() => expect(lastBody(calls).annualRate).toBe('0.1375'))
  })

  it('posts the term and frequency the API expects', async () => {
    const user = userEvent.setup()
    const { calls } = stubFetch(routes())
    const onDisbursed = vi.fn()

    render(<DisburseLoanForm onDisbursed={onDisbursed} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Disburse loan' }))

    await waitFor(() => expect(onDisbursed).toHaveBeenCalledOnce())
    expect(lastBody(calls)).toMatchObject({
      borrowerId: 3,
      loanNumber: 'L-2026-0001',
      principal: '30000',
      termPeriods: 40,
      frequency: 'WEEKLY',
    })
  })

  it('describes the term in the unit the chosen frequency actually uses',
    async () => {
      // "40 instalments" means ten months weekly and three years monthly. The
      // hint is what stops a clerk writing a three-year loan by accident.
      const user = userEvent.setup()
      stubFetch(routes())

      render(<DisburseLoanForm onDisbursed={() => {}} />)
      await screen.findByLabelText('Member')
      expect(screen.getByText('Weeks')).toBeInTheDocument()

      await user.selectOptions(screen.getByLabelText('Repayment frequency'), 'MONTHLY')
      expect(screen.getByText('Months')).toBeInTheDocument()
    })

  it('surfaces a rejected loan number rather than silently doing nothing',
    async () => {
      const user = userEvent.setup()
      stubFetch(routes({
        status: 409,
        body: {
          status: 409, error: 'Conflict',
          message: 'Loan number L-2026-0001 is already used',
          fieldErrors: {},
        },
      }))

      render(<DisburseLoanForm onDisbursed={() => {}} />)
      await fillValidForm(user)
      await user.click(screen.getByRole('button', { name: 'Disburse loan' }))

      expect(await screen.findByRole('alert'))
        .toHaveTextContent('already used')
    })

  it('disables the button while the request is in flight', async () => {
    // Two clicks would post two loans with the same number, and the second
    // would fail confusingly rather than doing nothing.
    const user = userEvent.setup()
    let release: (() => void) | undefined
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/borrowers')) {
        return { ok: true, status: 200, json: async () => MEMBERS } as Response
      }
      await new Promise<void>((resolve) => { release = resolve })
      return { ok: true, status: 201, json: async () => ({ id: 1 }) } as Response
    }))

    render(<DisburseLoanForm onDisbursed={() => {}} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Disburse loan' }))

    expect(await screen.findByRole('button', { name: 'Disbursing…' })).toBeDisabled()
    release?.()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Disburse loan' })).toBeEnabled())
  })
})
