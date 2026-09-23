package Katedra.Server.service;

import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class TemarioUrlExtractionService {

    private static final int TIMEOUT_MILLIS = 10000;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_EXTRACTED_CHARACTERS = 50000;
    private static final int MIN_EXTRACTED_CHARACTERS = 50;
    private static final List<String> NOISE_SELECTORS = List.of(
            "script", "style", "noscript", "svg", "canvas", "iframe",
            "nav", "header", "footer", "aside", "form", "button",
            "[role=navigation]", "[role=banner]", "[role=contentinfo]",
            ".menu", ".navbar", ".nav", ".sidebar", ".footer", ".header",
            ".cookie", ".cookies", ".advertisement", ".ads", ".ad"
    );
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .proxy(Proxy.NO_PROXY)
            .build();
    private final HostResolver hostResolver;
    private final PageFetcher pageFetcher;

    public TemarioUrlExtractionService() {
        this(InetAddress::getAllByName, TemarioUrlExtractionService::fetchPage);
    }

    TemarioUrlExtractionService(HostResolver hostResolver, PageFetcher pageFetcher) {
        this.hostResolver = hostResolver;
        this.pageFetcher = pageFetcher;
    }

    public ExtractedTemarioUrl extract(String rawUrl) {
        URI uri = validateUrl(rawUrl);
        try {
            Document document = fetchFollowingRedirects(uri);

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

    private static FetchedPage fetchPage(URI uri, List<InetAddress> validatedAddresses) throws IOException {
        OkHttpClient client = HTTP_CLIENT.newBuilder()
                .dns(pinnedDns(uri.getHost(), validatedAddresses))
                .build();
        Request request = new Request.Builder()
                .url(uri.toString())
                .header("User-Agent", "KatedraBot/1.0")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            int statusCode = response.code();
            String location = response.header("Location");
            if (statusCode < 200 || statusCode >= 300) {
                return new FetchedPage(statusCode, location, null);
            }

            ResponseBody body = response.body();
            if (body.contentLength() > MAX_BODY_BYTES) {
                throw new IOException("Respuesta HTTP demasiado grande");
            }
            byte[] content;
            try (InputStream stream = body.byteStream()) {
                content = stream.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (content.length > MAX_BODY_BYTES) {
                throw new IOException("Respuesta HTTP demasiado grande");
            }

            MediaType contentType = body.contentType();
            Charset charset = contentType == null ? null : contentType.charset();
            String charsetName = charset == null ? null : charset.name();
            Document document = Jsoup.parse(
                    new ByteArrayInputStream(content), charsetName, uri.toString());
            return new FetchedPage(statusCode, location, document);
        }
    }

    private static Dns pinnedDns(String host, List<InetAddress> validatedAddresses) {
        String expectedHost = normalizeHost(host);
        List<InetAddress> pinnedAddresses = List.copyOf(validatedAddresses);
        return requestedHost -> {
            if (!expectedHost.equals(normalizeHost(requestedHost))) {
                throw new UnknownHostException("Host no validado: " + requestedHost);
            }
            return pinnedAddresses;
        };
    }

    private Document fetchFollowingRedirects(URI initialUri) throws IOException {
        URI currentUri = initialUri;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            List<InetAddress> validatedAddresses = resolvePublicDestination(currentUri);
            FetchedPage response = pageFetcher.fetch(currentUri, validatedAddresses);
            int statusCode = response.statusCode();

            if (statusCode >= 300 && statusCode < 400) {
                if (redirectCount == MAX_REDIRECTS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demasiadas redirecciones");
                }
                currentUri = resolveRedirect(currentUri, response.location());
                continue;
            }
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("Respuesta HTTP no exitosa: " + statusCode);
            }
            return response.document();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demasiadas redirecciones");
    }

    private URI resolveRedirect(URI currentUri, String location) {
        if (location == null || location.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Redireccion URL invalida");
        }
        try {
            return validateUrl(currentUri.resolve(location.trim()).toString());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Redireccion URL invalida", ex);
        }
    }

    private List<InetAddress> resolvePublicDestination(URI uri) {
        String host = normalizeHost(uri.getHost());
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            throw forbiddenDestination();
        }

        try {
            InetAddress[] addresses = hostResolver.resolve(host);
            if (addresses.length == 0) {
                throw forbiddenDestination();
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw forbiddenDestination();
                }
            }
            return List.copyOf(Arrays.asList(addresses));
        } catch (UnknownHostException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo acceder a la URL", ex);
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isPublicIpv4(bytes);
        }
        if (bytes.length == 16) {
            return isPublicIpv6(bytes);
        }
        return false;
    }

    private boolean isPublicIpv4(byte[] address) {
        int first = Byte.toUnsignedInt(address[0]);
        int second = Byte.toUnsignedInt(address[1]);
        int third = Byte.toUnsignedInt(address[2]);

        if (first == 0 || first >= 224) {
            return false;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return false;
        }
        if (first == 192 && second == 0 && (third == 0 || third == 2)) {
            return false;
        }
        if (first == 192 && second == 88 && third == 99) {
            return false;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return false;
        }
        if (first == 198 && second == 51 && third == 100) {
            return false;
        }
        return !(first == 203 && second == 0 && third == 113);
    }

    private boolean isPublicIpv6(byte[] address) {
        int first = Byte.toUnsignedInt(address[0]);
        int second = Byte.toUnsignedInt(address[1]);
        int third = Byte.toUnsignedInt(address[2]);
        int fourth = Byte.toUnsignedInt(address[3]);

        boolean globalUnicast = (first & 0xe0) == 0x20;
        if (!globalUnicast) {
            return false;
        }
        if (first == 0x20 && second == 0x01 && (third & 0xfe) == 0) {
            return false;
        }
        if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) {
            return false;
        }
        if (first == 0x20 && second == 0x02) {
            return false;
        }
        return !(first == 0x3f && second == 0xff && (third & 0xf0) == 0);
    }

    private ResponseStatusException forbiddenDestination() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destino URL no permitido");
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    @FunctionalInterface
    interface PageFetcher {
        FetchedPage fetch(URI uri, List<InetAddress> validatedAddresses) throws IOException;
    }

    record FetchedPage(int statusCode, String location, Document document) {}

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
