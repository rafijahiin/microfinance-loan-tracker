import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import LoansPage from './LoansPage'
import { renderWithRouter } from '../test/renderWithRouter'
import { stubFetch } from '../test/fetchMock'
import type { Loan } from '../api/types'

function loan(over: Partial<Loan>): Loan {
  return {
    id: 1,
    loanNumber: 'L-0001',
    borrowerId: 1,
    borrowerName: 'Rahima Begum',
    principal: '30000',
    annualRate: '0.12',
    termPeriods: 40,
    frequency: 'WEEKLY',
    disbursedOn: '2026-01-15',
    status: 'ACTIVE',
    totalDue: '32769.23',
    totalPaid: '0.00',
    outstanding: '32769.23',
    overdue: '0.00',
    daysInArrears: 0,
    schedule: null,
    ...over,
  }
}

function renderLoans(loans: Loan[]) {
  stubFetch((url) =>
    url.includes('/api/loans')
      ? {
          body: {
            content: loans,
            totalElements: loans.length,
            totalPages: 1,
            number: 0,
            size: 50,
          },
        }
      : undefined,
  )
  return renderWithRouter(<LoansPage />)
}

describe('LoansPage', () => {
  it('shows the product in its own units, because 40 weekly is not 40 monthly',
    async () => {
      renderLoans([
        loan({ id: 1, loanNumber: 'L-0001', termPeriods: 40, frequency: 'WEEKLY' }),
        loan({ id: 2, loanNumber: 'L-0002', termPeriods: 12, frequency: 'MONTHLY' }),
      ])

      expect(await screen.findByText('40 weekly')).toBeInTheDocument()
      expect(screen.getByText('12 monthly')).toBeInTheDocument()
    })

  it('separates merely late from seriously late, the way PAR 30 does', async () => {
    // The thirty-day line is not cosmetic: it is the threshold the portfolio
    // figure is cut on, so the list has to agree with the dashboard about
    // which loans are in trouble.
    renderLoans([
      loan({ id: 1, loanNumber: 'L-A', daysInArrears: 0 }),
      loan({ id: 2, loanNumber: 'L-B', daysInArrears: 21, overdue: '1250.00' }),
      loan({ id: 3, loanNumber: 'L-C', daysInArrears: 123, overdue: '14933.32' }),
    ])

    expect(await screen.findByText('Current')).toBeInTheDocument()
    expect(screen.getByText('21 days late')).toBeInTheDocument()
    expect(screen.getByText('123 days late')).toBeInTheDocument()
  })

  it('labels a settled loan as settled rather than current', async () => {
    // A closed loan has zero days in arrears, so without checking status first
    // it would read as "Current" and look like live exposure.
    renderLoans([loan({ status: 'CLOSED', outstanding: '0.00', daysInArrears: 0 })])

    expect(await screen.findByText('Settled')).toBeInTheDocument()
    expect(screen.queryByText('Current')).not.toBeInTheDocument()
  })

  it('marks a written-off loan distinctly, since it leaves the PAR denominator',
    async () => {
      renderLoans([loan({ status: 'WRITTEN_OFF', daysInArrears: 200 })])

      expect(await screen.findByText('Written off')).toBeInTheDocument()
      expect(screen.queryByText('200 days late')).not.toBeInTheDocument()
    })

  it('shows a dash rather than 0.00 when nothing is overdue', async () => {
    // A column of 0.00 reads as a measured zero. A dash reads as "nothing to
    // see", which is what it means.
    renderLoans([loan({ overdue: '0.00' })])

    expect(await screen.findByText('—')).toBeInTheDocument()
  })

  it('offers the disburse flow from the list', async () => {
    renderLoans([loan({})])

    expect(await screen.findByRole('button', { name: 'Disburse a loan' }))
      .toBeInTheDocument()
  })

  it('says so plainly when there are no loans', async () => {
    renderLoans([])

    expect(await screen.findByText('No loans yet.')).toBeInTheDocument()
  })
})
