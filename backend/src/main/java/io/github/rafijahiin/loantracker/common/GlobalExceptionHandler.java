package io.github.rafijahiin.loantracker.common;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Turns exceptions into the single ApiError shape.
 *
 *  Deliberately does NOT carry the exception's own message into a 500: an
 *  unexpected failure can quote SQL, table names or beneficiary data, and this
 *  API serves financial records. The detail goes to the log, the client gets a
 *  generic line.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> onBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "Validation failed", fields));
    }

    /**
     * Two people changed the same loan at once and this request lost the race.
     *
     * 409 rather than 500: nothing is broken, the caller simply acted on a
     * balance that has since moved. Reloading and retrying is the correct
     * response, and the message says so rather than leaving a clerk staring at
     * "something went wrong" with a receipt in their hand.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> onConcurrentChange(
            ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent modification rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                409, "Conflict",
                "Someone else changed this loan while you were working on it. "
                + "Reload it and post the payment again."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden", "You may not perform this action"));
    }

    /**
     * The path exists but not for this verb, for example DELETE on a read-only
     * resource.
     *
     * Without this, Spring's own exception falls through to the catch-all below
     * and a caller using the wrong method is told the server broke. 405 with
     * the allowed methods says what to do instead. The same omission in another
     * codebase turned every unknown URL into a 500, which is how a missing
     * route gets mistaken for an outage.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> onWrongMethod(HttpRequestMethodNotSupportedException ex) {
        String allowed = ex.getSupportedHttpMethods() == null ? ""
                : ex.getSupportedHttpMethods().stream()
                        .map(Object::toString).sorted().collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of(405, "Method Not Allowed",
                        allowed.isEmpty()
                                ? "That method is not supported here"
                                : "That method is not supported here. Allowed: " + allowed));
    }

    /** No such path. A 404 is the honest answer; a 500 would send someone
     *  looking for a fault that does not exist. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> onNoRoute(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", "No such endpoint"));
    }

    /** Malformed JSON, or a value that cannot be read into the field it is
     *  meant for. The request is wrong, not the server. */
    @ExceptionHandler({HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> onUnreadableRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400, "Bad Request",
                "The request could not be read. Check the field types and the "
                + "JSON syntax."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "Something went wrong. The incident has been logged."));
    }
}
