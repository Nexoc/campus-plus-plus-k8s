package at.campus.backend.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * GlobalExceptionHandler
 * ==================================================
 *
 * Centralized exception-to-HTTP mapping.
 *
 * RESPONSIBILITIES
 * --------------------------------------------------
 * - Translates application exceptions to HTTP responses
 * - Produces consistent JSON error format
 * - Logs errors at the appropriate level
 *
 * IMPORTANT ARCHITECTURAL RULES
 * --------------------------------------------------
 * - Controllers MUST NOT catch exceptions
 * - Services MUST throw domain exceptions
 * - Repository MUST NOT throw HTTP-related exceptions
 *
 * ERROR LOGGING POLICY
 * --------------------------------------------------
 * - WARN  : expected business errors (403, 404)
 * - ERROR : unexpected failures (500)
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================================================
    // 403 FORBIDDEN
    // ==================================================

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            ForbiddenException ex
    ) {
        log.warn("Forbidden: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorBody(
                        HttpStatus.FORBIDDEN,
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(
            SecurityException ex
    ) {
        log.warn("Forbidden: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorBody(
                        HttpStatus.FORBIDDEN,
                        ex.getMessage()
                ));
    }

    // ==================================================
    // 404 NOT FOUND
    // ==================================================

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NotFoundException ex
    ) {
        log.warn("Not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorBody(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage()
                ));
    }

    // ==================================================
    // 400 BAD REQUEST
    // ==================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException ex
    ) {
        log.warn("Bad request: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex
    ) {
        log.warn("Invalid state: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage()
                ));
    }

    // ==================================================
    // 409 CONFLICT
    // ==================================================

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        
        if (status.is4xxClientError()) {
            log.warn("{}: {}", status.getReasonPhrase(), ex.getReason());
        } else {
            log.error("{}: {}", status.getReasonPhrase(), ex.getReason(), ex);
        }

        return ResponseEntity
                .status(status)
                .body(errorBody(
                        status,
                        ex.getReason() != null ? ex.getReason() : status.getReasonPhrase()
                ));
    }

    // ==================================================
    // 500 INTERNAL SERVER ERROR
    // ==================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex
    ) {
        log.error("Unhandled exception", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal server error"
                ));
    }

    // ==================================================
    // ERROR BODY FACTORY
    // ==================================================

    /**
     * Builds a standard error response body.
     *
     * Response format:
     * {
     *   "timestamp": "...",
     *   "status": 403,
     *   "error": "FORBIDDEN",
     *   "message": "Moderator role required"
     * }
     */
    private Map<String, Object> errorBody(
            HttpStatus status,
            String message
    ) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        );
    }
}
