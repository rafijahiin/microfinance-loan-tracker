export type Role = 'ADMIN' | 'PO_OFFICER'

export type LoanStatus = 'ACTIVE' | 'CLOSED' | 'WRITTEN_OFF'
export type RepaymentFrequency = 'WEEKLY' | 'MONTHLY'
export type InstalmentStatus = 'PENDING' | 'PARTIAL' | 'PAID'

export interface LoginResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  email: string
  role: Role
  partnerId: number | null
}

export interface Partner {
  id: number
  code: string
  name: string
  district: string
  active: boolean
}

export interface Borrower {
  id: number
  partnerId: number
  partnerCode: string | null
  memberCode: string
  name: string
  /** Last four digits only. The raw number is never sent by the API. */
  nationalIdMasked: string | null
  phone: string | null
  village: string | null
  union: string | null
  upazila: string | null
  district: string
  enrolledOn: string
}

export interface Instalment {
  instalmentNo: number
  dueOn: string
  principalDue: string
  interestDue: string
  amountDue: string
  amountPaid: string
  balance: string
  status: InstalmentStatus
  settledOn: string | null
}

export interface Loan {
  id: number
  loanNumber: string
  borrowerId: number
  borrowerName: string
  principal: string
  annualRate: string
  termPeriods: number
  frequency: RepaymentFrequency
  disbursedOn: string
  status: LoanStatus
  totalDue: string
  totalPaid: string
  outstanding: string
  overdue: string
  daysInArrears: number
  schedule: Instalment[] | null
}

export type AuditAction =
  | 'MEMBER_ENROLLED'
  | 'LOAN_DISBURSED'
  | 'REPAYMENT_POSTED'
  | 'LOAN_WRITTEN_OFF'

/** One entry in the append-only trail. `actorEmail` rather than a user id,
 *  because the record has to outlive the account. */
export interface AuditEvent {
  id: number
  occurredAt: string
  action: AuditAction
  actionLabel: string
  actorEmail: string
  actorRole: Role
  entityType: string
  entityId: number | null
  summary: string
  amount: string | null
}

export interface Repayment {
  id: number
  receiptNo: string
  receivedOn: string
  amount: string
  recordedBy: string | null
}

export interface PortfolioSummary {
  activeLoans: number
  outstanding: string
  overdue: string
  loansInArrears: number
  par30: string
  asOf: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** The single error shape the API returns for every failure. */
export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  fieldErrors: Record<string, string>
}
