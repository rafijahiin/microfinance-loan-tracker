-- Initial schema for the loan tracker.
--
-- Money is numeric(15,2), never float: a binary float cannot hold 0.10, and a
-- portfolio total built from thousands of such values does not reconcile.
-- 15 digits leaves room for a portfolio in the billions of taka at paisa
-- precision.

CREATE TABLE partner_organisation (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    district    VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    partner_id    BIGINT REFERENCES partner_organisation (id),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    -- Enforced here rather than only in the application. A PO_OFFICER row with
    -- a null partner would pass every scoping check by comparing null to null
    -- and see the whole portfolio. That must be impossible to write, not merely
    -- unlikely.
    CONSTRAINT ck_user_partner_matches_role CHECK (
        (role = 'ADMIN'      AND partner_id IS NULL) OR
        (role = 'PO_OFFICER' AND partner_id IS NOT NULL)
    )
);

CREATE TABLE borrower (
    id          BIGSERIAL PRIMARY KEY,
    partner_id  BIGINT       NOT NULL REFERENCES partner_organisation (id),
    member_code VARCHAR(30)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(20),
    village     VARCHAR(120),
    union_name  VARCHAR(120),
    upazila     VARCHAR(120),
    district    VARCHAR(100) NOT NULL,
    enrolled_on DATE         NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    -- Unique within a partner, not globally. Two partners each numbering their
    -- members from 1 is ordinary; a global constraint would reject real data.
    CONSTRAINT uq_borrower_partner_code UNIQUE (partner_id, member_code)
);

CREATE TABLE loan (
    id           BIGSERIAL PRIMARY KEY,
    loan_number  VARCHAR(40)   NOT NULL UNIQUE,
    borrower_id  BIGINT        NOT NULL REFERENCES borrower (id),
    principal    NUMERIC(15,2) NOT NULL,
    annual_rate  NUMERIC(6,4)  NOT NULL,
    term_months  INTEGER       NOT NULL,
    disbursed_on DATE          NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_loan_principal_positive CHECK (principal > 0),
    CONSTRAINT ck_loan_rate_sane          CHECK (annual_rate >= 0 AND annual_rate <= 1),
    CONSTRAINT ck_loan_term_positive      CHECK (term_months >= 1)
);

CREATE TABLE instalment (
    id            BIGSERIAL PRIMARY KEY,
    loan_id       BIGINT        NOT NULL REFERENCES loan (id) ON DELETE CASCADE,
    instalment_no INTEGER       NOT NULL,
    due_on        DATE          NOT NULL,
    principal_due NUMERIC(15,2) NOT NULL,
    interest_due  NUMERIC(15,2) NOT NULL,
    amount_due    NUMERIC(15,2) NOT NULL,
    amount_paid   NUMERIC(15,2) NOT NULL DEFAULT 0,
    settled_on    DATE,
    status        VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_instalment_loan_no UNIQUE (loan_id, instalment_no),
    -- The database refuses to hold a paid figure above what was due. The
    -- service rejects overpayment too, but a bug there would otherwise write a
    -- negative balance that every downstream total silently absorbs.
    CONSTRAINT ck_instalment_not_overpaid CHECK (amount_paid <= amount_due),
    CONSTRAINT ck_instalment_paid_positive CHECK (amount_paid >= 0)
);

CREATE TABLE repayment (
    id          BIGSERIAL PRIMARY KEY,
    loan_id     BIGINT        NOT NULL REFERENCES loan (id) ON DELETE CASCADE,
    receipt_no  VARCHAR(40)   NOT NULL UNIQUE,
    received_on DATE          NOT NULL,
    amount      NUMERIC(15,2) NOT NULL,
    recorded_by VARCHAR(200),
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_repayment_positive CHECK (amount > 0)
);

-- Indexes follow the queries the application actually issues: every listing is
-- filtered by partner, the portfolio scan is filtered by status, and the
-- schedule is always read by loan.
CREATE INDEX ix_borrower_partner   ON borrower (partner_id);
CREATE INDEX ix_loan_borrower      ON loan (borrower_id);
CREATE INDEX ix_loan_status        ON loan (status);
CREATE INDEX ix_instalment_loan    ON instalment (loan_id);
CREATE INDEX ix_instalment_due     ON instalment (due_on) WHERE status <> 'PAID';
CREATE INDEX ix_repayment_loan     ON repayment (loan_id);
