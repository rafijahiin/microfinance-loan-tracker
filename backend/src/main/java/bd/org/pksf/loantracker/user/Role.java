package bd.org.pksf.loantracker.user;

/** Who a user is allowed to be.
 *
 *  ADMIN sees the whole portfolio across every partner organisation.
 *  PO_OFFICER sees only the partner they belong to. The apex lender works
 *  through independent partner organisations, and one partner has no business
 *  reading another's borrower list.
 */
public enum Role {
    ADMIN,
    PO_OFFICER
}
