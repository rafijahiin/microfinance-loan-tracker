# Microfinance Loan Tracker

A loan tracking service for a **two-tier lender**: an apex body finances partner
organisations, and those partners on-lend to members in the field. The apex body
never meets the borrower, so every borrower, loan and repayment belongs to
exactly one partner, and that boundary is what authorisation is drawn along.

Spring Boot 3 and Java 21 on the back, React and TypeScript on the front,
PostgreSQL underneath, the whole stack up with one command.

**61 tests, all passing.** `mvn test` needs nothing but a JDK.

---

## Running it

```bash
cp .env.example .env        # then put a real signing key in JWT_SECRET
docker compose up --build
```

- Application: <http://localhost:3000>
- API documentation (Swagger UI): <http://localhost:8080/swagger-ui.html>

Demo accounts, seeded when `SEED_DEMO_DATA=true`:

| Account | Password | Sees |
|---|---|---|
| `admin@example.org` | `admin12345` | Every partner |
| `officer.rangpur@example.org` | `officer12345` | Shomota Unnayan Sangstha only |
| `officer.barishal@example.org` | `officer12345` | Nodi Jonopod Foundation only |

Sign in as an officer, then as the administrator, and compare the portfolio
figures. That difference is the authorisation model working.

### Without Docker

```bash
cd backend  && JWT_SECRET=$(openssl rand -base64 48) mvn spring-boot:run
cd frontend && npm install && npm run dev
```

The Vite dev server proxies `/api` to `localhost:8080`, so the browser stays on
one origin and CORS is not involved in development.

### Tests

```bash
cd backend && mvn test          # 61 tests
cd frontend && npm run typecheck
```

---

## What it does

**Disburse a loan** and the repayment schedule is generated from it. **Record a
repayment** and it settles against that schedule, oldest instalment first.
**Read the portfolio** and you get outstanding, arrears and PAR 30, scoped to
whoever is asking.

### Flat-rate schedules

Interest is charged on the original principal for the whole term, so every
instalment carries the same service charge. This is how a group loan is quoted
in this sector, and quoting a declining-balance figure against a flat-rate
product makes every instalment wrong.

```
total interest = principal x annual rate x term in years
instalment     = (principal + total interest) / number of instalments
```

The rounding is the part worth reading. Dividing by the term almost never comes
out exact, so the first n-1 instalments take the amount rounded **down** and the
final one takes whatever is left. Rounding every instalment independently would
give 10,000 over three months as 3,333.33 three times, which is 9,999.99: the
schedule would not add up to what the borrower was lent. Small per loan,
impossible to reconcile across a portfolio.

Month-end dates clamp rather than roll: a loan disbursed on 31 January falls due
on 28 February, not 3 March.

### Oldest-first allocation

A payment settles the oldest unpaid instalment before any later one. Applying it
to whichever instalment happens to be due this month would leave an older one
open, so the loan reports as in arrears while the member is paying every week,
and the arrears age never resets.

**Overpayment is refused, not held as a credit.** A member handing over more
than the loan owes is nearly always a keying error at the branch, and turning it
into an unexplained credit balance is how a ledger stops reconciling.

### PAR 30

Portfolio at Risk over 30 days is the **entire outstanding balance** of every
loan carrying an instalment more than thirty days late, over the total
outstanding. The whole balance counts, not only the instalments already missed:
the assumption is that a borrower a month behind puts the rest of the loan at
risk too. Counting only the missed instalments gives a much prettier number that
means something else.

Written-off loans are excluded. The loss on them has already been recognised, so
including them would count the same money twice and, worse, a write-off would
*improve* the ratio by leaving the denominator.

---

## Decisions worth defending

**Money is `BigDecimal` and `numeric(15,2)`, never a float.** A binary float
cannot represent 0.10, so a schedule built by repeated addition drifts and the
final instalment lands a few poisha out. The API also returns amounts as
decimal *strings*, and the frontend only converts them at the point of display,
because parsing to a JavaScript number puts the value straight back through the
float that was avoided in the first place.

**Cross-partner reads answer 404, not 403.** A 403 confirms the record exists,
which lets an officer walk the id space and learn how many loans another partner
has written. Both "does not exist" and "not yours" return the same 404.

**The role/partner rule is a database CHECK constraint, not only application
code.** A `PO_OFFICER` row with a null partner would pass every scoping check by
comparing null to null and see the entire portfolio. That has to be impossible
to write, not merely unlikely.

**`open-in-view` is off.** Left on, the persistence session stays open for the
whole request, so a lazy association touched during JSON serialisation quietly
issues another query and an N+1 never surfaces as an error. Turning it off made
the borrower listing throw `LazyInitializationException` during development,
which is exactly the point: the repository was fixed to fetch what it needs
rather than the symptom being hidden.

**The loan listing pages IDs first, then fetches.** One query with
`left join fetch l.instalments` and a `Pageable` is the classic trap: the join
multiplies rows by instalment, so `LIMIT` would cut the result mid-loan.
Hibernate avoids returning wrong data by pulling the entire result set into
memory and paginating there, warning `HHH000104` as it goes. That works on six
seeded loans and falls over on a real portfolio. Two queries, each correct.

**`ddl-auto: validate`, never `update`.** Flyway owns the schema. Letting
Hibernate alter a database holding financial records means its shape is decided
by whichever version of the code booted last. `validate` makes the application
refuse to start when code and schema have drifted, which is the moment you want
to find out.

**No default JWT secret anywhere.** The application fails fast if
`app.jwt.secret` is missing or under 32 characters, and compose refuses to start
without it. A committed fallback key is how a service ends up signing production
tokens with a key that is public on GitHub.

**Login gives one answer for "no such user", "wrong password" and "disabled".**
Distinguishing them turns the endpoint into a way to enumerate who holds an
account. There is a test asserting the two messages are byte-identical.

**A receipt number can only be posted once.** Without that, a retry after a
timeout collects the same money twice in the ledger.

---

## Layout

```
backend/src/main/java/bd/org/pksf/loantracker/
├── common/      Errors, the single ApiError shape, money helpers, auditing
├── security/    JWT issuing and parsing, the filter, the partner AccessGuard
├── user/        Accounts and roles
├── partner/     Partner organisations
├── borrower/    Members
├── loan/        Loans, instalments, repayments, schedules, portfolio
└── config/      OpenAPI, demo seeder

frontend/src/
├── api/         Typed client and the API's response types
├── auth/        Session context
└── pages/       Portfolio, loans, loan detail, members, login
```

### API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auth/login` | Returns a bearer token |
| `GET` | `/api/auth/me` | The caller as the server resolved them |
| `GET` | `/api/partners` | An officer sees only their own |
| `POST` | `/api/partners` | Admin only |
| `GET` | `/api/borrowers?q=` | Search by name or member code |
| `POST` | `/api/borrowers` | Enrol a member |
| `GET` | `/api/borrowers/{id}/loans` | |
| `GET` | `/api/loans?status=` | Summary rows, no schedule |
| `GET` | `/api/loans/{id}` | With the full schedule |
| `POST` | `/api/loans` | Disburse and generate the schedule |
| `POST` | `/api/loans/{id}/repayments` | Settles oldest first |
| `GET` | `/api/loans/{id}/repayments` | |
| `POST` | `/api/loans/{id}/write-off` | Admin only |
| `GET` | `/api/portfolio/summary?asOf=` | Outstanding, arrears, PAR 30 |

Every failure returns the same shape, so a client never has to guess which of
several error formats it is parsing:

```json
{
  "timestamp": "2026-09-11T09:12:33Z",
  "status": 409,
  "error": "Conflict",
  "message": "Payment of 12000.01 is more than the 12000.00 still owed on this loan",
  "fieldErrors": {}
}
```

An unexpected 500 deliberately does **not** carry the exception's message: it
can quote SQL, table names or borrower data. The detail goes to the log.

---

## Tests

| Suite | What it pins down |
|---|---|
| `ScheduleGeneratorTest` | Flat interest, exact reconciliation, remainder placement, month-end clamping, rejected inputs |
| `InstalmentTest` | Part payment, settlement, surplus left for the next row, overdue boundaries |
| `LoanDerivedFiguresTest` | Outstanding against overdue, arrears measured from the oldest unpaid instalment |
| `AuthIntegrationTest` | Token issue, partner claim, identical failure messages, disabled accounts, tampered tokens |
| `PartnerScopingIntegrationTest` | One partner cannot read, list or write into another's data |
| `LoanLifecycleIntegrationTest` | Disburse to settlement over HTTP, overpayment, duplicate receipts, write-off, validation |
| `PortfolioIntegrationTest` | PAR 30 on a fixed date with figures checkable by hand |
| `MigrationMatchesEntitiesTest` | The Flyway migration and the entity mappings have not drifted |
| `SmokeTest` | The application boots on a real port, serves health and OpenAPI, and authenticates over TCP |

Two of these exist because of what the rest of the suite cannot see.
`MigrationMatchesEntitiesTest` reads the hand-written migration and the entity
mappings and asserts they agree, because the suite runs against a
Hibernate-generated schema while production runs `ddl-auto: validate` against
Flyway's: those can drift, and when they do every test still passes and the
application refuses to start on the next deployment. `SmokeTest` boots a real
HTTP server, and it earned its place immediately by catching a missing actuator
dependency that would have failed the container healthcheck at
`docker compose up`.

The scoping tests are the ones worth reading first. A mistake there does not
throw an error, it quietly shows one lender another lender's borrower list.

**On the database used in tests.** The suite runs against in-memory H2 so that
`mvn test` passes on a clean checkout with nothing installed but a JDK.
Testcontainers against the real PostgreSQL image would be better in CI: it would
also exercise the Flyway migrations and catch dialect differences. The honest
consequence of the choice made here is that the migrations are exercised when
the stack comes up under compose, not by the test suite.

---

## What I would do next

- **Testcontainers in CI**, keeping H2 for the fast local loop.
- **Optimistic locking** (`@Version`) on `Loan`. Two clerks posting against the
  same loan at the same instant is a real scenario at a branch, and today the
  second write wins silently.
- **An outbox or audit table for repayments.** `created_at` tells you when a row
  was written, not who changed what. Financial records get argued about.
- **Recoveries against written-off loans**, which are posted separately from a
  schedule and are currently refused outright.
- **Declining-balance products** alongside flat rate. The schedule generator is
  already isolated behind one interface for this reason.
- **Rate limiting on `/api/auth/login`.** Constant-time failure messages stop
  enumeration; they do not stop brute force.
