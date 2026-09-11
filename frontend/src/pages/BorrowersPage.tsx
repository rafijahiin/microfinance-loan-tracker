import { useState } from 'react'
import { api } from '../api/client'
import type { Borrower, Page } from '../api/types'
import { useAsync } from '../useAsync'
import { day } from '../format'

export default function BorrowersPage() {
  const [term, setTerm] = useState('')
  const [query, setQuery] = useState('')

  const { data, error, loading } = useAsync<Page<Borrower>>(
    () => api.get<Page<Borrower>>(
      `/api/borrowers?size=50${query ? `&q=${encodeURIComponent(query)}` : ''}`),
    [query],
  )

  return (
    <>
      <h1>Members</h1>
      <p className="sub">
        Search by name or member code. National ID numbers are stored only as a
        keyed hash, so only the last four digits can ever be displayed.
      </p>

      <form
        className="row-actions"
        style={{ marginBottom: 18 }}
        onSubmit={(e) => {
          e.preventDefault()
          setQuery(term.trim())
        }}
      >
        <div className="field">
          <label htmlFor="q">Search</label>
          <input id="q" value={term} onChange={(e) => setTerm(e.target.value)}
                 placeholder="Rahima, or M-0001" />
        </div>
        <button type="submit">Search</button>
        {query && (
          <button type="button" className="ghost"
                  onClick={() => { setTerm(''); setQuery('') }}>
            Clear
          </button>
        )}
      </form>

      {error && <div className="error">{error}</div>}
      {loading ? (
        <p className="notice">Loading members...</p>
      ) : !data || data.content.length === 0 ? (
        <div className="card notice">No members matched.</div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>National ID</th>
                <th>Village</th>
                <th>District</th>
                <th>Partner</th>
                <th>Enrolled</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((b) => (
                <tr key={b.id}>
                  <td>{b.memberCode}</td>
                  <td>{b.name}</td>
                  {/* Masked at the API, not here. The browser never receives
                      the full number, so a screenshot cannot leak it. */}
                  <td className="masked">{b.nationalIdMasked ?? '\u2014'}</td>
                  <td>{b.village ?? '\u2014'}</td>
                  <td>{b.district}</td>
                  <td>{b.partnerCode ?? '\u2014'}</td>
                  <td>{day(b.enrolledOn)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
