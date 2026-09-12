package io.github.rafijahiin.loantracker.common;

/** Thrown when a requested record does not exist, or exists but is outside the
 *  caller's partner organisation. Both are answered with 404 rather than 403,
 *  so that probing IDs cannot be used to discover which borrowers belong to
 *  another partner. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
