package Katedra.Server.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemarioUrlExtractionServiceTest {

    private final TemarioUrlExtractionService service = new TemarioUrlExtractionService();

    @Test
    void shouldRejectBlankUrl() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.extract(" "));

        assertThat(exception.getReason()).isEqualTo("URL requerida");
    }

    @Test
    void shouldRejectNonHttpUrl() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.extract("ftp://example.com/file"));

        assertThat(exception.getReason()).isEqualTo("Solo se permiten URLs http/https");
    }

    @Test
    void shouldRejectInvalidUrl() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.extract("not-a-url"));

        assertThat(exception.getReason()).isEqualTo("URL invalida");
    }
}
