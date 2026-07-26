package Katedra.Server.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

@Service
public class TemarioUrlExtractionService {

    private static final int TIMEOUT_MILLIS = 10000;
    private static final int MAX_EXTRACTED_CHARACTERS = 50000;
    private static final int MIN_EXTRACTED_CHARACTERS = 50;
    private static final List<String> NOISE_SELECTORS = List.of(
            "script", "style", "noscript", "svg", "canvas", "iframe",
            "nav", "header", "footer", "aside", "form", "button",
            "[role=navigation]", "[role=banner]", "[role=contentinfo]",
            ".menu", ".navbar", ".nav", ".sidebar", ".footer", ".header",
            ".cookie", ".cookies", ".advertisement", ".ads", ".ad"
    );

    public ExtractedTemarioUrl extract(String rawUrl) {
        URI uri = validateUrl(rawUrl);
        try {
            Document document = Jsoup.connect(uri.toString())
                    .userAgent("KatedraBot/1.0")
                    .timeout(TIMEOUT_MILLIS)
                    .maxBodySize(2 * 1024 * 1024)
                    .followRedirects(true)
                    .get();

            clean(document);
            String text = extractMainText(document);
            if (text.length() < MIN_EXTRACTED_CHARACTERS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La pagina no contiene texto util");
            }

            return new ExtractedTemarioUrl(
                    uri.toString(),
                    resolveTitle(document, uri),
                    truncate(text)
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo acceder a la URL", ex);
        }
    }

    private URI validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL requerida");
        }

        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL invalida");
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten URLs http/https");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL invalida", ex);
        }
    }

    private void clean(Document document) {
        for (String selector : NOISE_SELECTORS) {
            document.select(selector).remove();
        }
    }

    private String extractMainText(Document document) {
        Element main = document.selectFirst("main, article, [role=main]");
        Element source = main != null ? main : document.body();
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La pagina no contiene texto util");
        }
        return normalize(source.text());
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text) {
        if (text.length() <= MAX_EXTRACTED_CHARACTERS) {
            return text;
        }
        return text.substring(0, MAX_EXTRACTED_CHARACTERS).trim();
    }

    private String resolveTitle(Document document, URI uri) {
        String title = document.title();
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return uri.getHost();
    }

    public record ExtractedTemarioUrl(
        String url,
        String title,
        String text
    ) {}
}
