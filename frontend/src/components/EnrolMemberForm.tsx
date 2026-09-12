import { useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { Borrower, Page, Partner } from '../api/types'
import { useAsync } from '../useAsync'
import { useFormSubmit } from '../useFormSubmit'
import { Field } from './Field'

const today = () => new Date().toISOString().slice(0, 10)

export function EnrolMemberForm({ onEnrolled }: { onEnrolled: () => void }) {
  // An officer belongs to exactly one partner, so this list has one entry for
  // them and several for an administrator. Reading it from the API rather than
  // asking the user to type an id means the scoping rule decides the options.
  const partners = useAsync<Page<Partner>>(
    () => api.get<Page<Partner>>('/api/partners?size=100'), [])

  const [partnerId, setPartnerId] = useState('')
  const [memberCode, setMemberCode] = useState('')
  const [name, setName] = useState('')
  const [nationalId, setNationalId] = useState('')
  const [district, setDistrict] = useState('')
  const [enrolledOn, setEnrolledOn] = useState(today())
  const [phone, setPhone] = useState('')
  const [village, setVillage] = useState('')

  const { busy, error, fieldErrors, submit } = useFormSubmit()

  const options = partners.data?.content ?? []
  const chosen = partnerId || (options.length === 1 ? String(options[0]?.id) : '')

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void submit(
      () =>
        api.post<Borrower>('/api/borrowers', {
          partnerId: Number(chosen),
          memberCode: memberCode.trim(),
          name: name.trim(),
          nationalId: nationalId.trim(),
          district: district.trim(),
          enrolledOn,
          phone: phone.trim() || null,
          village: village.trim() || null,
        }),
      onEnrolled,
    )
  }

  return (
    <form onSubmit={onSubmit} aria-label="Enrol a member">
      {error && <div className="error" role="alert">{error}</div>}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
        <Field id="partner" label="Partner organisation" error={fieldErrors.partnerId}>
          {(p) => (
            <select {...p} required value={chosen}
                    onChange={(e) => setPartnerId(e.target.value)}>
              <option value="">Select…</option>
              {options.map((o) => (
                <option key={o.id} value={o.id}>{o.code} · {o.name}</option>
              ))}
            </select>
          )}
        </Field>

        <Field id="memberCode" label="Member code" error={fieldErrors.memberCode}
               hint="Unique within this partner">
          {(p) => (
            <input {...p} required value={memberCode}
                   onChange={(e) => setMemberCode(e.target.value)} />
          )}
        </Field>

        <Field id="name" label="Name" error={fieldErrors.name}>
          {(p) => (
            <input {...p} required value={name}
                   onChange={(e) => setName(e.target.value)} />
          )}
        </Field>

        <Field id="nationalId" label="National ID" error={fieldErrors.nationalId}
               hint="Stored as a keyed hash, never as the number itself">
          {(p) => (
            <input {...p} required inputMode="numeric" value={nationalId}
                   onChange={(e) => setNationalId(e.target.value)} />
          )}
        </Field>

        <Field id="district" label="District" error={fieldErrors.district}>
          {(p) => (
            <input {...p} required value={district}
                   onChange={(e) => setDistrict(e.target.value)} />
          )}
        </Field>

        <Field id="enrolledOn" label="Enrolled on" error={fieldErrors.enrolledOn}>
          {(p) => (
            <input {...p} required type="date" value={enrolledOn}
                   onChange={(e) => setEnrolledOn(e.target.value)} />
          )}
        </Field>

        <Field id="village" label="Village (optional)" error={fieldErrors.village}>
          {(p) => (
            <input {...p} value={village}
                   onChange={(e) => setVillage(e.target.value)} />
          )}
        </Field>

        <Field id="phone" label="Phone (optional)" error={fieldErrors.phone}>
          {(p) => (
            <input {...p} value={phone}
                   onChange={(e) => setPhone(e.target.value)} />
          )}
        </Field>
      </div>

      <button type="submit" disabled={busy}>
        {busy ? 'Enrolling…' : 'Enrol member'}
      </button>
    </form>
  )
}
