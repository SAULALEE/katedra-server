package Katedra.Server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ensures every exception reaches the client as a JSON body with a "message" field,
 * regardless of servlet container error-page behavior. Extending
 * ResponseEntityExceptionHandler keeps Spring MVC's own status codes (400 for
 * validation/malformed-body errors, 404 for unmapped routes, etc.) while
 * normalizing the response shape via handleExceptionInternal below.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request
    ) {
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", Instant.now().toString());
        responseBody.put("status", statusCode.value());
        responseBody.put("message", ex.getMessage());

        return new ResponseEntity<>(responseBody, headers, statusCode);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", ex.getReason());

        return ResponseEntity.status(status).body(body);
    }

    // Catches everything else (NPEs, DB constraint violations, etc.) so raw exception
    // messages never reach the client. Spring MVC's own exceptions (validation errors,
    // malformed JSON, unmapped routes...) never reach here — they're resolved by the
    // superclass's handlers, which route through handleExceptionInternal above.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception ex) {
        logger.error("Unhandled exception", ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 500);
        body.put("message", "Ha ocurrido un error interno. Inténtalo de nuevo más tarde.");

        return ResponseEntity.status(500).body(body);
    }
}
