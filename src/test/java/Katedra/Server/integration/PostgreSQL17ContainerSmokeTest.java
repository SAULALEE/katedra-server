package Katedra.Server.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSQL17ContainerSmokeTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void shouldStartAnIsolatedPostgresql17Instance() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                    POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SHOW server_version_num")) {
            assertTrue(result.next());
            assertTrue(result.getString(1).startsWith("17"));
        }
    }
}
