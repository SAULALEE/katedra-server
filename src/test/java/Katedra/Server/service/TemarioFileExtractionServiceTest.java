package Katedra.Server.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemarioFileExtractionServiceTest {

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARACTERS = 50000;

    private final TemarioFileExtractionService service = new TemarioFileExtractionService();

    @Test
    void shouldExtractMarkdownAsUtf8() {
        var file = new MockMultipartFile(
                "file", "temario.md", "text/markdown",
                "# Álgebra\nContenido académico".getBytes(StandardCharsets.UTF_8));

        var extracted = service.extract(file);

        assertThat(extracted.filename()).isEqualTo("temario.md");
        assertThat(extracted.text()).contains("Álgebra", "Contenido académico");
    }

    @Test
    void shouldRejectPdfWithMoreThanTenPages() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 0; page < 11; page++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            pdf = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "temario.pdf", "application/pdf", pdf);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertThat(exception.getReason()).isEqualTo("El PDF no puede superar 10 paginas");
    }

    @Test
    void shouldExtractPdfContent() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Temario de algoritmos y estructuras de datos");
                content.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "temario.pdf", "application/pdf", pdf);

        var extracted = service.extract(file);

        assertThat(extracted.text()).contains("Temario de algoritmos y estructuras de datos");
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        var file = new MockMultipartFile("file", "temario.txt", "text/plain", "texto".getBytes());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertThat(exception.getReason()).isEqualTo("Formato de archivo no soportado");
    }

    @Test
    void shouldRejectFileLargerThanTenMegabytes() {
        var file = new MockMultipartFile(
                "file", "temario.md", "text/markdown", new byte[MAX_FILE_BYTES + 1]);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exception.getReason()).isEqualTo("El archivo no puede superar 10 MB");
    }

    @Test
    void shouldRejectContentThatDoesNotMatchExtension() {
        var file = new MockMultipartFile(
                "file", "temario.pdf", "application/pdf",
                "Esto es texto plano, no un PDF".getBytes(StandardCharsets.UTF_8));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertThat(exception.getReason()).isEqualTo("El tipo MIME no coincide con la extension");
    }

    @Test
    void shouldRejectDeclaredMimeTypeThatDoesNotMatchExtension() {
        var file = new MockMultipartFile(
                "file", "temario.md", "application/pdf",
                "# Temario válido".getBytes(StandardCharsets.UTF_8));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertThat(exception.getReason()).isEqualTo("El tipo MIME no coincide con la extension");
    }

    @Test
    void shouldLimitExtractedMarkdownText() {
        var file = new MockMultipartFile(
                "file", "temario.md", "text/markdown",
                "a".repeat(MAX_EXTRACTED_CHARACTERS + 1000).getBytes(StandardCharsets.UTF_8));

        var extracted = service.extract(file);

        assertThat(extracted.text()).hasSize(MAX_EXTRACTED_CHARACTERS);
    }

    @Test
    void shouldRejectMarkdownWithInvalidUtf8() {
        var file = new MockMultipartFile(
                "file", "temario.md", "text/markdown", new byte[]{(byte) 0xc3, (byte) 0x28});

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertThat(exception.getReason()).isEqualTo("El archivo Markdown debe usar UTF-8 valido");
    }

    @Test
    void shouldRejectMarkdownContainingBinaryControlCharacters() {
        var file = new MockMultipartFile(
                "file", "temario.md", "text/markdown",
                new byte[]{'T', 'e', 'x', 't', 'o', 0, 1});

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.extract(file));

        assertThat(exception.getReason()).isEqualTo("El archivo Markdown no contiene texto valido");
    }

    @Test
    void shouldLimitTextExtractedFromDocx() throws Exception {
        String longText = "contenido-academico ".repeat(4000);
        var file = new MockMultipartFile(
                "file", "temario.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocx(longText));

        var extracted = service.extract(file);

        assertThat(extracted.text()).hasSizeLessThanOrEqualTo(MAX_EXTRACTED_CHARACTERS);
    }

    @Test
    void shouldExtractDocxContent() throws Exception {
        var file = new MockMultipartFile(
                "file", "temario.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocx("Temario de estructuras de datos"));

        var extracted = service.extract(file);

        assertThat(extracted.text()).contains("Temario de estructuras de datos");
    }

    private byte[] createDocx(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            writeEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            writeEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body>
                    </w:document>
                    """.formatted(text));
        }
        return output.toByteArray();
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
