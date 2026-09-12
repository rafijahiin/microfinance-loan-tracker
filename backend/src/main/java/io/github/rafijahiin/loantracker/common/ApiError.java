package io.github.rafijahiin.loantracker.common;

import java.time.Instant;
import java.util.Map;

/** One response shape for every error the API returns, so a client never has to
 *  guess which of several error formats it is parsing. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }

    public static ApiError of(int status, String error, String message,
                              Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
