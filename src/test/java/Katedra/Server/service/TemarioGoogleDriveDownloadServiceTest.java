package Katedra.Server.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemarioGoogleDriveDownloadServiceTest {

    private final TemarioGoogleDriveDownloadService service = new TemarioGoogleDriveDownloadService();

    @Test
    void shouldExtractFileIdFromFileUrl() {
        String fileId = service.extractFileId("https://drive.google.com/file/d/abc123/view?usp=sharing");

        assertThat(fileId).isEqualTo("abc123");
    }

    @Test
    void shouldExtractFileIdFromOpenUrl() {
        String fileId = service.extractFileId("https://drive.google.com/open?id=abc123");

        assertThat(fileId).isEqualTo("abc123");
    }

    @Test
    void shouldRejectNonDriveUrl() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.extractFileId("https://example.com/file/d/abc123/view"));

        assertThat(exception.getReason()).isEqualTo("URL de Google Drive invalida");
    }

    @Test
    void shouldRejectDriveUrlWithoutFileId() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.extractFileId("https://drive.google.com/drive/my-drive"));

        assertThat(exception.getReason()).isEqualTo("No se pudo extraer el fileId de Google Drive");
    }
}
