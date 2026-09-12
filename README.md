# Microfinance Loan Tracker

A loan tracking service for a **two-tier lender**: an apex body finances partner
organisations, and those partners on-lend to members in the field. The apex body
never meets the borrower, so every borrower, loan and repayment belongs to
exactly one partner, and that boundary is what authorisation is drawn along.

Spring Boot 3 and Java 21 on the back, React and TypeScript on the front,
PostgreSQL underneath, the whole stack up with one command.

**97 backend tests and 55 frontend tests, all passing.** `mvn test` needs nothing but a JDK.

---

## Running it

The quickest way needs **only a JDK**. No database to install, no Docker.

```bash
cd backend  && mvn spring-boot:run -Ph2 -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev
```

- Application: <http://localhost:5173>
- API documentation (Swagger UI): <http://localhost:8080/swagger-ui.html>

That runs against in-memory H2 with demo data. For the real thing on
PostgreSQL, or the whole stack in containers, see
[Other ways to run it](#other-ways-to-run-it).

Demo accounts, seeded when `SEED_DEMO_DATA=true`:

| Account | Password | Sees |
|---|---|---|
| `admin@example.org` | `admin12345` | Every partner |
| `officer.rangpur@example.org` | `officer12345` | Shomota Unnayan Sangstha only |
| `officer.barishal@example.org` | `officer12345` | Nodi Jonopod Foundation only |

Sign in as an officer, then as the administrator, and compare the portfolio
figures. That difference is the authorisation model working.

### Other ways to run it

**Against a real PostgreSQL.** Point it at any instance; Flyway creates the
schema on first boot.

```bash
cd backend && DB_URL=jdbc:postgresql://localhost:5432/loantracker \
              DB_USER=loantracker DB_PASSWORD=loantracker \
              JWT_SECRET=$(openssl rand -base64 48) \
              NID_PEPPER=$(openssl rand -base64 32) \
              SEED_DEMO_DATA=true mvn spring-boot:run
```

**The hosted demo.** `render.yaml` is a Render blueprint: one web service, no
database. It runs the `demo` Spring profile, which is in-memory H2 with the
seed data, so there is nothing to provision and nothing that expires after
ninety days. `backend/Dockerfile.demo` is the image; it is separate from the
main Dockerfile because that one builds the jar anything real would ship, with
no embedded database in it.

The free instance sleeps after about fifteen minutes idle, so the first visit
after a quiet period waits roughly a minute while it starts. The login screen
says so after three seconds rather than leaving a spinner, because a visitor
staring at one concludes the thing is broken.

The demo relaxes nothing about secrets: `JWT_SECRET` and `NID_PEPPER` are still
required and the application refuses to start without them. Render generates
both. A demo shipping a default key would teach the wrong lesson to anyone
reading this repository to see how it is done.

**The whole stack in containers.**

```bash
cp .env.example .env        # set JWT_SECRET and NID_PEPPER
docker compose up --build
```

Application on <http://localhost:3000>, API on <http://localhost:8080>.

The Vite dev server proxies `/api` to `localhost:8080`, so the browser stays on
one origin and CORS is not involved in development.

### Tests

```bash
cd backend  && mvn test         # 97 tests, in-memory H2, no setup
cd frontend && npm test         # 55 tests
cd frontend && npm run typecheck
```

The same suite also runs against a real PostgreSQL, with the real migrations
and `ddl-auto=validate`, by overriding the datasource:

```bash
cd backend && TEST_DB_URL=jdbc:postgresql://localhost:5432/loantracker_test \
              TEST_DB_DRIVER=org.postgresql.Driver \
              TEST_DB_USER=loantracker TEST_DB_PASSWORD=loantracker \
              TEST_DDL_AUTO=validate TEST_FLYWAY=true mvn test
```

CI runs both. **The PostgreSQL run is not ceremony: it has already caught two
defects that H2 reported as passing.** See below.

---

## What it does

**Disburse a loan** and the repayment schedule is generated from it. **Record a
repayment** and it settles against that schedule, oldest instalment first.
**Read the portfolio** and you get outstanding, arrears and PAR 30, scoped to
whoever is asking.

### Weekly or monthly, flat rate

A loan repays **weekly** or **monthly**. Weekly is the norm for group lending
here, because collection happens at the weekly samity meeting; monthly suits
larger individual and enterprise loans. The frequency lives on the loan, not in
the generator's assumptions.

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

**The term is converted to years using the loan's own frequency.** Forty weekly
instalments is 40/52 of a year, not 40/12. Pricing weekly terms on a monthly
divisor would overcharge every weekly loan by more than four times, which is
the single most damaging thing that can go wrong when a monthly product gains a
weekly sibling. There is a test pinned to a figure worked out by hand:
30,000 at 12 per cent flat over 40 weeks is 2,769.23 of interest.

### Oldest-first allocation

A payment settles the oldest unpaid instalment before any later one. Applying it
to whichever instalment happens to be due this month would leave an older one
open, so the loan reports as in arrears while the member is paying every week,
and the arrears age never resets.

**Overpayment is refused, not held as a credit.** A member handing over more
than the loan owes is nearly always a keying error at the branch, and turning it
into an unexplained credit balance is how a ledger stops reconciling.

### The national ID is never stored

A member's NID is the most sensitive field here: a lifelong government
identifier, reused across every service she touches. A leaked lender database
full of them is materially worse than one full of names. So the number itself is
never persisted. Two derived values are:

- a **keyed hash**, which duplicate detection compares, and
- the **last four digits**, which staff see when confirming identity.

HMAC-SHA256 with a secret pepper, not a bare digest. A plain SHA-256 of a
national ID is not protection: the format is short and structured enough that
the whole space can be enumerated and matched against a stolen table in minutes.
The pepper is not in the database, so the table alone gives an attacker nothing
to match against. It is required and has no default, for the same reason the
signing key has none.

Numbers are normalised before hashing, so `1990 1234 56789` and `1990-1234-56789`
collide as they should. Uniqueness is **per partner**: a woman can genuinely be a
member of two organisations, and a global constraint would both block a
legitimate enrolment and leak, through the rejection, that she is a member
elsewhere. The duplicate error deliberately does not echo the number back.

### Who did what

`created_at` and `updated_at` say when a row last changed. They do not say who
changed it, and after an update they no longer say what it said before. On a
table of financial records that is the question that actually gets asked,
months later, when a member disputes a receipt.

So every write records a **domain event**: member enrolled, loan disbursed,
repayment posted, loan written off. Deliberately not column diffs. "Receipt
R-001, 3,000 posted against L-0001, by officer.rangpur" is what someone asks
for; a row of before-and-after values answers a different question and buries
this one.

Four properties worth stating:

- **Append-only.** No update path, no delete endpoint, no setters. A trail that
  can be edited proves nothing, because the first question about any entry
  would be whether it is the original.
- **Same transaction as the change.** `Propagation.MANDATORY`, so an entry
  cannot be written outside the transaction it describes and a rolled-back
  change takes its entry with it. A trail asserting a repayment that never
  landed is worse than no trail, because it would be believed.
- **Scoped like everything else.** An officer sees their own partner's
  activity. An unscoped trail would be the one place an officer could learn
  about another organisation's lending.
- **No secrets.** The enrolment entry carries the masked national ID that staff
  already see, never the number that was typed. An audit table is a long-lived,
  widely-read copy of whatever you put in it.

The actor is stored as an email rather than a foreign key to `app_user`,
because the trail has to outlive the account: a clerk who leaves still posted
the receipts they posted.

It is visible in two places: **Recent activity** on the portfolio page, and
**History** on each loan.

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

**A 401 means two different things, and the client tells them apart.** Carrying
a token, it has expired: clear the session and say so. Carrying none, a sign-in
was refused and the server's own message is the useful one. Treating both alike
told someone typing a wrong password that their session had expired, which is
nonsense to a person who has not signed in yet. A test found it.

**Login gives one answer for "no such user", "wrong password" and "disabled".**
Distinguishing them turns the endpoint into a way to enumerate who holds an
account. There is a test asserting the two messages are byte-identical.

**The loan carries an optimistic lock, and a repayment forces it forward.**
Two clerks posting against one loan in the same second is a Thursday, not a
thought experiment, and without this the second write wins silently: one receipt
vanishes from the balance while staying in the cash book. The subtlety is that
`@Version` alone would not help. A repayment usually touches only instalment
rows, and modifying a child collection does not make the parent row dirty, so
the version would never advance. `RepaymentService` takes
`OPTIMISTIC_FORCE_INCREMENT` on the loan explicitly. A test removes that line
and proves the suite notices.

**The interest rate is a fraction, and the API enforces it.** `0.12` is twelve
per cent, and the field is capped at `1.0000`. Without that bound a caller who
means twelve per cent and sends `12` gets a loan at 1200 per cent, or, on an API
that divides by 100 internally, a loan at 0.12 per cent. Both are silent and
both are wrong, so the value is rejected rather than quietly accepted.

**A receipt number can only be posted once.** Without that, a retry after a
timeout collects the same money twice in the ledger.

---

## Layout

```
backend/src/main/java/io/github/rafijahiin/loantracker/
├── common/      Errors, the single ApiError shape, money helpers, auditing
├── security/    JWT issuing and parsing, the filter, the partner AccessGuard
├── user/        Accounts and roles
├── partner/     Partner organisations
├── borrower/    Members
├── loan/        Loans, instalments, repayments, schedules, portfolio
├── audit/       The append-only trail of who did what
└── config/      OpenAPI, demo seeder

frontend/src/
├── api/         Typed client and the API's response types
├── auth/        Session context
├── components/  Enrol and disburse forms, field, disclosure
├── test/        jsdom setup and the fetch stub
└── pages/       Portfolio, loans, loan detail, members, login
```

### Frontend tests

32 of them, run by `npm test` and in CI. `fetchMock` stubs the network rather
than the API client, so the bearer token, the single error shape and the 401
handling are exercised rather than mocked away. The ones worth reading:

- the disburse form sends `0.1200` when a clerk types `12`, which is the trap
  the API's `1.0000` cap exists to close;
- a failed write does not report success, because closing the panel and
  reloading would tell a clerk a member was enrolled when she was not;
- the client treats an environment with no `localStorage` as signed out rather
  than throwing, which is not hypothetical: jsdom here has none.

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
| `POST` | `/api/loans` | Disburse; takes `termPeriods` and `frequency` |
| `POST` | `/api/loans/{id}/repayments` | Settles oldest first |
| `GET` | `/api/loans/{id}/repayments` | |
| `POST` | `/api/loans/{id}/write-off` | Admin only |
| `GET` | `/api/portfolio/summary?asOf=` | Outstanding, arrears, PAR 30 |
| `GET` | `/api/audit` | Recent activity, scoped. Read only by design |
| `GET` | `/api/loans/{id}/audit` | That loan's history |

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
| `ScheduleGeneratorTest` | Flat interest, exact reconciliation, remainder placement, month-end clamping, weekly vs monthly pricing, rejected inputs |
| `NationalIdProtectorTest` | Determinism, that the pepper participates, normalisation, masking, refusal of a weak pepper |
| `BorrowerNationalIdIntegrationTest` | The raw number is never stored or returned, duplicates refused per partner, the error is not a membership oracle |
| `InstalmentTest` | Part payment, settlement, surplus left for the next row, overdue boundaries |
| `LoanDerivedFiguresTest` | Outstanding against overdue, arrears measured from the oldest unpaid instalment |
| `AuthIntegrationTest` | Token issue, partner claim, identical failure messages, disabled accounts, tampered tokens |
| `PartnerScopingIntegrationTest` | One partner cannot read, list or write into another's data |
| `LoanLifecycleIntegrationTest` | Disburse to settlement over HTTP, overpayment, duplicate receipts, write-off, validation |
| `PortfolioIntegrationTest` | PAR 30 on a fixed date with figures checkable by hand |
| `MigrationMatchesEntitiesTest` | The Flyway migration and the entity mappings have not drifted |
| `ConcurrentRepaymentTest` | A repayment advances the loan's version, and the second of two concurrent writers is refused rather than ignored |
| `AuditTrailIntegrationTest` | Every write records its actor, the trail is scoped and append-only, a refused change leaves no entry, and no national ID reaches it |
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

**On the two database runs.** Locally the suite uses in-memory H2, so
`mvn test` passes on a clean checkout with nothing installed but a JDK. CI runs
the identical suite a second time against a real PostgreSQL service with the
real Flyway migrations and `ddl-auto=validate`. Every value in the test
datasource config has an H2 default and an environment override, so there is
one suite and no duplicated setup.

That second run is there because **H2 is a liar by omission**, and it has proved
it twice:

- `GET /api/borrowers` with no search term returned **500 on PostgreSQL** while
  passing on H2. A null bind parameter arrives untyped, and inside `lower()`
  PostgreSQL infers `bytea`, so `lower(bytea) does not exist`. The fix was to
  stop passing null at all.
- `SmokeTest` deleted partners while borrowers still referenced them. H2 allowed
  it; PostgreSQL refused, correctly. The deletion order now lives in one
  `DatabaseCleaner` so two test classes cannot disagree about it.

Neither defect was visible to any amount of H2 testing.

---

## Continuous integration

`.github/workflows/ci.yml` runs three jobs on every push and pull request:
backend `mvn verify`, frontend `npm ci` then typecheck then build, and a build
of both container images.

`npm ci` rather than `npm install`: it installs exactly what the lockfile pins
and fails if the two disagree, so CI keeps testing what a developer actually
gets. The image build job exists because a Dockerfile that has drifted from the
source tree is otherwise only discovered by whoever next runs `docker compose`.

## A note on the visual identity

The palette is PKSF's green, `#00783c`, sampled from their master logo, and the
typefaces are the Quicksand and Roboto pairing used on pksf.org.bd. The domain
this application models is theirs, so it seemed right to look like it belongs to
that world.

Deliberately not used: the hexagonal emblem, and the organisation's name as this
application's own. **This is an independent portfolio project, not affiliated
with, endorsed by, or produced for Palli Karma-Sahayak Foundation.** That line
appears in the running application too.

## What I would do next

- **Testcontainers in CI**, keeping H2 for the fast local loop.
- **Recoveries against written-off loans**, which are posted separately from a
  schedule and are currently refused outright.
- **Declining-balance products** alongside flat rate. The schedule generator is
  already isolated behind one interface, and adding the weekly frequency proved
  the seam holds.
- **Rotating the national ID pepper.** Today a new pepper invalidates every
  existing hash, so a rotation needs the numbers re-supplied. A versioned
  pepper column would let old and new coexist during a migration.
- **Rate limiting on `/api/auth/login`.** Constant-time failure messages stop
  enumeration; they do not stop brute force.
