-- Two changes, both additive where they can be.
--
-- 1. National ID protection. The number itself is never stored: only a keyed
--    hash for duplicate detection and the last four digits for staff to
--    confirm identity. See NationalIdProtector for why a bare digest would not
--    be protection.
--
-- 2. Repayment frequency. Weekly collection at the samity meeting is the norm
--    for group lending, and term_months could not express it. The column is
--    renamed rather than replaced so existing rows keep their values, and the
--    default of MONTHLY is what those rows already meant.

ALTER TABLE borrower ADD COLUMN national_id_hash   VARCHAR(64);
ALTER TABLE borrower ADD COLUMN national_id_masked VARCHAR(40);

-- One member, one national ID, within a partner. Enforced here and not only in
-- the service: a duplicate enrolment inflates member counts, and in a
-- programme judged on outreach that is the number people care about.
CREATE UNIQUE INDEX uq_borrower_partner_nid
    ON borrower (partner_id, national_id_hash)
    WHERE national_id_hash IS NOT NULL;

ALTER TABLE loan RENAME COLUMN term_months TO term_periods;
ALTER TABLE loan ADD COLUMN frequency VARCHAR(10) NOT NULL DEFAULT 'MONTHLY';

-- The default exists to carry existing rows across. New rows always state a
-- frequency explicitly, so it is dropped to stop the column silently
-- defaulting when an insert forgets it.
ALTER TABLE loan ALTER COLUMN frequency DROP DEFAULT;

ALTER TABLE loan DROP CONSTRAINT ck_loan_term_positive;
ALTER TABLE loan ADD CONSTRAINT ck_loan_term_positive CHECK (term_periods >= 1);
ALTER TABLE loan ADD CONSTRAINT ck_loan_frequency
    CHECK (frequency IN ('WEEKLY', 'MONTHLY'));
