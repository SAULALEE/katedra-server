package Katedra.Server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class FileUploadConfigurationTest {

    @Test
    void shouldLimitMultipartUploadsBeforeApplicationProcessing() throws IOException {
        Properties properties = new Properties();
        Path resource = Path.of("src", "main", "resources", "application.properties");
        try (InputStream input = Files.newInputStream(resource)) {
            properties.load(input);
        }

        assertEquals("10MB", properties.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("11MB", properties.getProperty("spring.servlet.multipart.max-request-size"));
    }
}
