package bd.org.pksf.loantracker.security;

import bd.org.pksf.loantracker.common.NotFoundException;
import org.springframework.stereotype.Component;

/** One place where "may this caller touch this partner's data" is decided.
 *
 *  Spread across controllers, this check gets forgotten on the one endpoint
 *  nobody reviewed. Centralising it means a new endpoint either calls the guard
 *  or visibly does not.
 */
@Component
public class AccessGuard {

    /** The partner id to filter a listing by: null for an ADMIN, which the
     *  repository queries read as "no filter". */
    public Long scopeOf(AuthenticatedUser user) {
        return user.isAdmin() ? null : user.partnerId();
    }

    /** Answers a cross-partner read with 404, not 403.
     *
     *  403 confirms the record exists, which lets an officer walk the id space
     *  and learn how many loans another partner has written. 404 tells them
     *  only that it is not theirs to see.
     */
    public void assertCanAccess(AuthenticatedUser user, Long ownerPartnerId,
                                String what, Object id) {
        if (user.isAdmin()) {
            return;
        }
        if (ownerPartnerId == null || !ownerPartnerId.equals(user.partnerId())) {
            throw new NotFoundException(what, id);
        }
    }
}
