package Katedra.Server.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Service
public class TemarioFileExtractionService {

    private static final int MAX_PDF_PAGES = 10;
    private static final int TIKA_WRITE_LIMIT = -1;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "md");

    private final Tika tika = new Tika();
    private final AutoDetectParser parser = new AutoDetectParser();

    public ExtractedTemarioFile extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo requerido");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de archivo no soportado");
        }

        try {
            String detectedType = detectType(file);
            if ("pdf".equals(extension)) {
                validatePdfPageLimit(file);
            }

            String text = "md".equals(extension) ? readMarkdown(file) : parseWithTika(file);
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo extraer texto del archivo");
            }

            return new ExtractedTemarioFile(originalFilename, detectedType, text.trim());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | TikaException | SAXException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo procesar el archivo", ex);
        }
    }

    private String detectType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        }
    }

    private void validatePdfPageLimit(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El PDF no puede superar 10 paginas");
            }
        }
    }

    private String parseWithTika(MultipartFile file) throws IOException, TikaException, SAXException {
        BodyContentHandler handler = new BodyContentHandler(TIKA_WRITE_LIMIT);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            parser.parse(inputStream, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }

    private String readMarkdown(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "temario";
        }
        return originalFilename.replace("\\", "/").substring(originalFilename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private String getExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record ExtractedTemarioFile(
        String filename,
        String contentType,
        String text
    ) {}
}
