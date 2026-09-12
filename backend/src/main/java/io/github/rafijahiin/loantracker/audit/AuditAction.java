package io.github.rafijahiin.loantracker.audit;

/**
 * The events worth recording.
 *
 * Deliberately domain events, not column diffs. "Repayment posted, 3,000, by
 * officer.rangpur" is what someone asks for when a member disputes a receipt.
 * A row of before-and-after column values answers a different, less useful
 * question, and buries the answer to this one.
 */
public enum AuditAction {

    MEMBER_ENROLLED("Member enrolled"),
    LOAN_DISBURSED("Loan disbursed"),
    REPAYMENT_POSTED("Repayment posted"),
    LOAN_WRITTEN_OFF("Loan written off");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
