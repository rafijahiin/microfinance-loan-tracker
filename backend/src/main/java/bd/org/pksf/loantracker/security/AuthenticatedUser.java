package bd.org.pksf.loantracker.security;

import bd.org.pksf.loantracker.user.Role;

/** The caller, as resolved from a verified token. */
public record AuthenticatedUser(Long id, String email, Role role, Long partnerId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
