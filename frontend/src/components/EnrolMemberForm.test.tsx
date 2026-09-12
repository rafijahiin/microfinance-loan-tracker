import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EnrolMemberForm } from './EnrolMemberForm'
import { stubFetch, lastBody } from '../test/fetchMock'

const PARTNERS = {
  content: [
    { id: 7, code: 'PO-001', name: 'Shomota', district: 'Rangpur', active: true },
  ],
  totalElements: 1, totalPages: 1, number: 0, size: 20,
}

function routes(post?: { status: number; body: unknown }) {
  return (url: string, init?: RequestInit) => {
    if (url.includes('/api/partners')) return { body: PARTNERS }
    if (url.includes('/api/borrowers') && init?.method === 'POST') {
      return post ?? { status: 201, body: { id: 1 } }
    }
    return undefined
  }
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(await screen.findByLabelText('Partner organisation'), '7')
  await user.type(screen.getByLabelText('Member code'), 'M-0001')
  await user.type(screen.getByLabelText('Name'), 'Rahima Begum')
  await user.type(screen.getByLabelText('National ID'), '1990123456789')
  await user.type(screen.getByLabelText('District'), 'Rangpur')
}

describe('EnrolMemberForm', () => {
  it('offers the partners the API says the caller may use', async () => {
    // Not a free-text partner id: an officer is scoped to one organisation and
    // the server decides which, so the options come from it.
    stubFetch(routes())
    render(<EnrolMemberForm onEnrolled={() => {}} />)

    expect(await screen.findByRole('option', { name: 'PO-001 · Shomota' }))
      .toBeInTheDocument()
  })

  it('posts what the API expects and reports success once', async () => {
    const user = userEvent.setup()
    const { calls } = stubFetch(routes())
    const onEnrolled = vi.fn()

    render(<EnrolMemberForm onEnrolled={onEnrolled} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Enrol member' }))

    await waitFor(() => expect(onEnrolled).toHaveBeenCalledOnce())
    expect(lastBody(calls)).toMatchObject({
      partnerId: 7,
      memberCode: 'M-0001',
      name: 'Rahima Begum',
      nationalId: '1990123456789',
      district: 'Rangpur',
    })
  })

  it("shows the server's per-field messages against the right fields", async () => {
    // The rules live on the server. Restating them here would be a second
    // implementation that drifts, so the form renders whatever it is told.
    const user = userEvent.setup()
    stubFetch(routes({
      status: 400,
      body: {
        status: 400, error: 'Bad Request', message: 'Validation failed',
        fieldErrors: { memberCode: 'must not be blank' },
      },
    }))

    render(<EnrolMemberForm onEnrolled={() => {}} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Enrol member' }))

    expect(await screen.findByText('must not be blank')).toBeInTheDocument()
    expect(screen.getByLabelText('Member code')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows a business-rule refusal, such as a duplicate national ID', async () => {
    const user = userEvent.setup()
    stubFetch(routes({
      status: 409,
      body: {
        status: 409, error: 'Conflict',
        message: 'A member with this national ID is already enrolled under this partner',
        fieldErrors: {},
      },
    }))

    render(<EnrolMemberForm onEnrolled={() => {}} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Enrol member' }))

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('already enrolled under this partner')
  })

  it('does not report success when the server refused', async () => {
    const user = userEvent.setup()
    stubFetch(routes({ status: 409, body: { status: 409, message: 'no', fieldErrors: {} } }))
    const onEnrolled = vi.fn()

    render(<EnrolMemberForm onEnrolled={onEnrolled} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Enrol member' }))

    await screen.findByRole('alert')
    // Closing the panel and reloading the list on a failure would tell the
    // clerk the member was enrolled when she was not.
    expect(onEnrolled).not.toHaveBeenCalled()
  })

  it('says the national ID is not stored as typed', async () => {
    stubFetch(routes())
    render(<EnrolMemberForm onEnrolled={() => {}} />)

    expect(await screen.findByText(/keyed hash, never as the number itself/))
      .toBeInTheDocument()
  })
})
