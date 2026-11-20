package Katedra.Server.controller;

import Katedra.Server.dto.HealthResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Liveness probe for the free-tier deployment, which suspends the service after ~15 minutes
 * without traffic. An external cron pings this every 10 minutes so the first real request never
 * pays the cold start.
 *
 * <p>The DataSource is validated rather than merely returning a constant: a 503 here is what tells
 * the monitor that the managed MySQL dropped, which would otherwise only surface as a failing
 * request from a user. Render wakes the instance on the inbound request regardless of the status
 * code, so reporting the failure honestly costs nothing.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /** Seconds allowed for the driver's validation round trip. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<HealthResponseDTO> check() {
        return isDatabaseReachable()
                ? ResponseEntity.ok(new HealthResponseDTO("UP", "UP"))
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new HealthResponseDTO("DEGRADED", "DOWN"));
    }

    private boolean isDatabaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }
}
