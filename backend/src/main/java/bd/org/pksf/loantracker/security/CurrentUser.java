package bd.org.pksf.loantracker.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the caller out of the security context.
 *
 *  Services take the caller as an argument rather than reaching for the context
 *  themselves, which keeps them unit-testable without a security context. This
 *  component exists so the controllers have one place to get it from.
 */
@Component
public class CurrentUser {

    public AuthenticatedUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException(
                    "No authenticated user on a secured endpoint. This means the "
                    + "endpoint was left out of the security configuration.");
        }
        return user;
    }
}
