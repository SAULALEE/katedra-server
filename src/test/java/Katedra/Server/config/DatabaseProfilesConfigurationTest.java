package Katedra.Server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import Katedra.Server.repository.PostgreSqlUsoDiarioUpsert;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class DatabaseProfilesConfigurationTest {

    @Test
    void shouldKeepMysqlAsDefaultAndSeparateVendorSpecificConfiguration() throws IOException {
        Properties common = load("application.properties");
        Properties mysql = load("application-mysql.properties");
        Properties postgresql = load("application-postgresql.properties");

        assertEquals("mysql", common.getProperty("spring.profiles.default"));
        assertFalse(common.containsKey("spring.datasource.url"));
        assertFalse(common.containsKey("spring.datasource.driver-class-name"));
        assertFalse(common.containsKey("spring.flyway.locations"));

        assertEquals("com.mysql.cj.jdbc.Driver", mysql.getProperty("spring.datasource.driver-class-name"));
        assertEquals("classpath:db/migration", mysql.getProperty("spring.flyway.locations"));
        assertEquals("${DB_PASSWORD}", mysql.getProperty("spring.datasource.password"));

        assertEquals("org.postgresql.Driver", postgresql.getProperty("spring.datasource.driver-class-name"));
        assertEquals("classpath:db/migration-postgresql", postgresql.getProperty("spring.flyway.locations"));
        assertEquals("${POSTGRES_DB_PASSWORD}", postgresql.getProperty("spring.datasource.password"));
    }

    @Test
    void shouldIsolateSupabaseConfigurationAndRequireSsl() throws IOException {
        Properties supabase = load("application-supabase.properties");

        assertEquals(
                "jdbc:postgresql://${SUPABASE_DB_HOST}:${SUPABASE_DB_PORT:5432}/"
                        + "${SUPABASE_DB_NAME:postgres}?sslmode=require",
                supabase.getProperty("spring.datasource.url"));
        assertEquals("${SUPABASE_DB_USER}", supabase.getProperty("spring.datasource.username"));
        assertEquals("${SUPABASE_DB_PASSWORD}", supabase.getProperty("spring.datasource.password"));
        assertEquals("org.postgresql.Driver",
                supabase.getProperty("spring.datasource.driver-class-name"));
        assertEquals("classpath:db/migration-postgresql",
                supabase.getProperty("spring.flyway.locations"));
        assertEquals("false", supabase.getProperty("spring.flyway.baseline-on-migrate"));
        assertEquals("${SUPABASE_DB_POOL_MAX_SIZE:5}",
                supabase.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertEquals("${SUPABASE_DB_POOL_MIN_IDLE:1}",
                supabase.getProperty("spring.datasource.hikari.minimum-idle"));
    }

    @Test
    void shouldUsePostgresqlUpsertForSupabaseProfile() {
        Profile profile = PostgreSqlUsoDiarioUpsert.class.getAnnotation(Profile.class);

        assertTrue(profile != null);
        assertTrue(java.util.List.of(profile.value()).contains("postgresql"));
        assertTrue(java.util.List.of(profile.value()).contains("supabase"));
    }

    private Properties load(String resourceName) throws IOException {
        Path resource = Path.of("src", "main", "resources", resourceName);
        assertTrue(Files.isRegularFile(resource), resourceName + " must exist");
        try (InputStream input = Files.newInputStream(resource)) {
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        }
    }
}
