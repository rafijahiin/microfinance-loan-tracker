package io.github.rafijahiin.loantracker.common;

/** A request that is well-formed but would break a rule of the domain, for
 *  example paying more than a loan still owes. Distinct from a validation
 *  error: the payload was fine, the state of the world says no. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
