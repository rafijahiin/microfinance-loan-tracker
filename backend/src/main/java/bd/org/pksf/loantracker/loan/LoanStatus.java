package bd.org.pksf.loantracker.loan;

public enum LoanStatus {
    /** Disbursed and still owing. */
    ACTIVE,
    /** Every instalment settled. */
    CLOSED,
    /** Abandoned as uncollectable. Kept, never deleted: writing a loan off is
     *  an accounting event that has to stay on the record. */
    WRITTEN_OFF
}
