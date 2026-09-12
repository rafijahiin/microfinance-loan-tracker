import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Disclosure } from './Disclosure'

describe('Disclosure', () => {
  it('starts closed and opens on click', async () => {
    const user = userEvent.setup()
    render(<Disclosure label="Enrol a member">{() => <p>the form</p>}</Disclosure>)

    expect(screen.queryByText('the form')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Enrol a member' }))
    expect(screen.getByText('the form')).toBeInTheDocument()
  })

  it('tells assistive technology whether it is open', async () => {
    // Without aria-expanded a screen reader announces a button that appears to
    // do nothing, with no way to tell it has already been pressed.
    const user = userEvent.setup()
    render(<Disclosure label="Open">{() => <p>body</p>}</Disclosure>)

    const button = screen.getByRole('button')
    expect(button).toHaveAttribute('aria-expanded', 'false')

    await user.click(button)
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true')
  })

  it('lets the content close it, which is how a form dismisses itself on success',
    async () => {
      const user = userEvent.setup()
      render(
        <Disclosure label="Open">
          {(close) => <button type="button" onClick={close}>done</button>}
        </Disclosure>,
      )

      await user.click(screen.getByRole('button', { name: 'Open' }))
      await user.click(screen.getByRole('button', { name: 'done' }))

      expect(screen.queryByRole('button', { name: 'done' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Open' })).toBeInTheDocument()
    })
})
