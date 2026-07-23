package Katedra.Server.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemarioUrlExtractionServiceTest {

    private final TemarioUrlExtractionService service = new TemarioUrlExtractionService();

    @Test
    void shouldFetchCleanAndNormalizeRemotePageContent() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/temario", exchange -> {
            requests.incrementAndGet();
            byte[] body = """
                    <html><head><title>Estructuras de datos</title></head>
                    <body>
                      <nav>Menú que debe eliminarse</nav>
                      <main>
                        <h1>Pilas y colas</h1>
                        <p>Una pila sigue el principio LIFO.</p>
                        <p>Una cola sigue el principio FIFO.</p>
                      </main>
                      <script>contenidoInvalido()</script>
                    </body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/temario";
            var extracted = service.extract(url);

            assertThat(requests).hasValue(1);
            assertThat(extracted.url()).isEqualTo(url);
            assertThat(extracted.title()).isEqualTo("Estructuras de datos");
            assertThat(extracted.text())
                    .isEqualTo("Pilas y colas Una pila sigue el principio LIFO. Una cola sigue el principio FIFO.")
                    .doesNotContain("Menú", "contenidoInvalido");
        } finally {
            server.stop(0);
        }
    }

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
