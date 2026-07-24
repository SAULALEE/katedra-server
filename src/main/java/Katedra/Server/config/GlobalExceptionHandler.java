package Katedra.Server.config;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ensures every ResponseStatusException reaches the client as a JSON body with
 * a "message" field, regardless of servlet container error-page behavior.
 * Without this, only the generic HTTP reason phrase (e.g. "Unauthorized",
 * "Conflict") reached the frontend, no matter what reason text a service set.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", ex.getReason());

        return ResponseEntity.status(status).body(body);
    }
}
