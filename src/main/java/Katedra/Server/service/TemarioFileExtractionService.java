package Katedra.Server.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.Tika;
import org.apache.tika.exception.WriteLimitReachedException;
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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TemarioFileExtractionService {

    private static final int MAX_PDF_PAGES = 10;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARACTERS = 50000;
    private static final String GENERIC_BINARY_TYPE = "application/octet-stream";
    private static final Map<String, Set<String>> ALLOWED_MEDIA_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword", "application/x-tika-msoffice"),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml"),
            "md", Set.of("text/markdown", "text/x-markdown", "text/plain")
    );

    private final Tika tika = new Tika();
    private final AutoDetectParser parser = new AutoDetectParser();

    public ExtractedTemarioFile extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo requerido");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El archivo no puede superar 10 MB");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (!ALLOWED_MEDIA_TYPES.containsKey(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de archivo no soportado");
        }
        validateDeclaredType(extension, file.getContentType());

        try {
            boolean markdown = "md".equals(extension);
            String detectedType = markdown ? "text/markdown" : normalizeMediaType(detectType(file));
            if (!markdown) {
                validateDetectedType(extension, detectedType);
                if ("pdf".equals(extension)) {
                    validatePdfPageLimit(file);
                }
            }

            String text = markdown ? readMarkdown(file) : parseWithTika(file);
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo extraer texto del archivo");
            }

            return new ExtractedTemarioFile(originalFilename, detectedType, truncate(text.trim()));
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
        BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_CHARACTERS);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            try {
                parser.parse(inputStream, handler, metadata, new ParseContext());
            } catch (SAXException ex) {
                if (!WriteLimitReachedException.isWriteLimitReached(ex)) {
                    throw ex;
                }
            }
        }
        return handler.toString();
    }

    private String readMarkdown(MultipartFile file) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
            if (containsBinaryControlCharacters(text)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El archivo Markdown no contiene texto valido");
            }
            return text;
        } catch (CharacterCodingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo Markdown debe usar UTF-8 valido", ex);
        }
    }

    private boolean containsBinaryControlCharacters(String text) {
        return text.chars().anyMatch(character ->
                Character.isISOControl(character)
                        && character != '\n'
                        && character != '\r'
                        && character != '\t');
    }

    private void validateDeclaredType(String extension, String contentType) {
        String normalizedType = normalizeMediaType(contentType);
        if (normalizedType.isBlank() || GENERIC_BINARY_TYPE.equals(normalizedType)) {
            return;
        }
        validateTypeMatchesExtension(extension, normalizedType);
    }

    private void validateDetectedType(String extension, String detectedType) {
        validateTypeMatchesExtension(extension, detectedType);
    }

    private void validateTypeMatchesExtension(String extension, String mediaType) {
        if (!ALLOWED_MEDIA_TYPES.get(extension).contains(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo MIME no coincide con la extension");
        }
    }

    private String normalizeMediaType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parametersStart = contentType.indexOf(';');
        String baseType = parametersStart >= 0 ? contentType.substring(0, parametersStart) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String text) {
        if (text.length() <= MAX_EXTRACTED_CHARACTERS) {
            return text;
        }
        return text.substring(0, MAX_EXTRACTED_CHARACTERS).trim();
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
