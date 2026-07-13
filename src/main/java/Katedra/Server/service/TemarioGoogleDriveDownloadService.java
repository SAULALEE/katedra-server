package Katedra.Server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemarioGoogleDriveDownloadService {

    private static final int TIMEOUT_MILLIS = 10000;
    private static final int MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024;
    private static final String PRIVATE_FILE_MESSAGE =
            "El archivo de Google Drive debe estar como: Cualquier persona con el enlace puede ver";
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("/file/d/([^/]+)");
    private static final Pattern ID_QUERY_PATTERN = Pattern.compile("[?&]id=([^&]+)");

    public DownloadedDriveFile download(String publicUrl) {
        String fileId = extractFileId(publicUrl);
        String downloadUrl = "https://drive.google.com/uc?export=download&id=" + fileId;

        try {
            HttpURLConnection connection = open(downloadUrl);
            if (connection.getResponseCode() >= 400) {
                throw privateFileException();
            }

            String contentType = connection.getContentType();
            byte[] bytes = readBytes(connection);
            if (bytes.length == 0 || isDriveHtmlResponse(contentType, bytes)) {
                throw privateFileException();
            }

            String filename = resolveFilename(connection, fileId);
            return new DownloadedDriveFile(fileId, new InMemoryMultipartFile(filename, contentType, bytes));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw privateFileException();
        }
    }

    public String extractFileId(String publicUrl) {
        URI uri = validateDriveUrl(publicUrl);
        String raw = uri.toString();

        Matcher filePathMatcher = FILE_PATH_PATTERN.matcher(raw);
        if (filePathMatcher.find()) {
            return decode(filePathMatcher.group(1));
        }

        Matcher idQueryMatcher = ID_QUERY_PATTERN.matcher(raw);
        if (idQueryMatcher.find()) {
            return decode(idQueryMatcher.group(1));
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo extraer el fileId de Google Drive");
    }

    private URI validateDriveUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de Google Drive requerida");
        }

        try {
            URI uri = new URI(publicUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de Google Drive invalida");
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!("http".equals(normalizedScheme) || "https".equals(normalizedScheme))
                    || !normalizedHost.endsWith("drive.google.com")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de Google Drive invalida");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de Google Drive invalida", ex);
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "KatedraBot/1.0");
        return connection;
    }

    private byte[] readBytes(HttpURLConnection connection) throws IOException {
        int contentLength = connection.getContentLength();
        if (contentLength > MAX_DOWNLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo de Google Drive supera el tamano permitido");
        }

        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOWNLOAD_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo de Google Drive supera el tamano permitido");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private boolean isDriveHtmlResponse(String contentType, byte[] bytes) {
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            return false;
        }
        String preview = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        return preview.contains("<html") || preview.contains("google drive") || preview.contains("accounts.google.com");
    }

    private String resolveFilename(HttpURLConnection connection, String fileId) {
        String disposition = connection.getHeaderField("Content-Disposition");
        if (disposition != null) {
            Matcher matcher = Pattern.compile("filename\\*?=(?:UTF-8''|\"?)([^\";]+)").matcher(disposition);
            if (matcher.find()) {
                return decode(matcher.group(1).replace("\"", ""));
            }
        }
        return fileId + ".pdf";
    }

    private ResponseStatusException privateFileException() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, PRIVATE_FILE_MESSAGE);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public record DownloadedDriveFile(String fileId, MultipartFile file) {}

    private record InMemoryMultipartFile(
            String originalFilename,
            String contentType,
            byte[] bytes
    ) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}
