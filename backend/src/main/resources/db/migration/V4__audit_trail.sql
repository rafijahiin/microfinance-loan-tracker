-- Append-only record of who did what.
--
-- created_at and updated_at say when a row last changed, not who changed it or
-- what it said before. On a table of financial records that is the question
-- that actually gets asked, months later, when a member disputes a receipt.
--
-- actor_email rather than a foreign key to app_user on purpose: the trail has
-- to outlive the account. A clerk who leaves still posted the receipts they
-- posted, and removing their user row must not blank the record of it.
CREATE TABLE audit_event (
    id           BIGSERIAL PRIMARY KEY,
    occurred_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    action       VARCHAR(30)  NOT NULL,
    actor_email  VARCHAR(255) NOT NULL,
    actor_role   VARCHAR(20)  NOT NULL,
    partner_id   BIGINT,
    entity_type  VARCHAR(30)  NOT NULL,
    entity_id    BIGINT,
    summary      VARCHAR(500) NOT NULL,
    amount       NUMERIC(15,2),

    CONSTRAINT ck_audit_action CHECK (action IN (
        'MEMBER_ENROLLED', 'LOAN_DISBURSED', 'REPAYMENT_POSTED', 'LOAN_WRITTEN_OFF'
    ))
);

-- No foreign key to partner_organisation. A partner row could in principle be
-- removed during data cleanup, and the trail of what was done under it must
-- survive that rather than cascade away with it.
CREATE INDEX ix_audit_recent ON audit_event (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_partner ON audit_event (partner_id, occurred_at DESC);
CREATE INDEX ix_audit_entity ON audit_event (entity_type, entity_id, occurred_at DESC);
