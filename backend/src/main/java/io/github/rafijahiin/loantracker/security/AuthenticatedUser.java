package io.github.rafijahiin.loantracker.security;

import io.github.rafijahiin.loantracker.user.Role;

/** The caller, as resolved from a verified token. */
public record AuthenticatedUser(Long id, String email, Role role, Long partnerId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
