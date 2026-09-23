package Katedra.Server.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
class TemarioUrlExtractionServiceTest {

    private final TemarioUrlExtractionService service = new TemarioUrlExtractionService();

    @Test
    void shouldFetchCleanAndNormalizeRemotePageContent() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        TemarioUrlExtractionService.FetchedPage response = successfulPage("""
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
                """);
        TemarioUrlExtractionService publicService = new TemarioUrlExtractionService(
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                (uri, validatedAddresses) -> {
                    requests.incrementAndGet();
                    return response;
                });
        String url = "https://public.example/temario";

        var extracted = publicService.extract(url);

        assertThat(requests).hasValue(1);
        assertThat(extracted.url()).isEqualTo(url);
        assertThat(extracted.title()).isEqualTo("Estructuras de datos");
        assertThat(extracted.text())
                .isEqualTo("Pilas y colas Una pila sigue el principio LIFO. Una cola sigue el principio FIFO.")
                .doesNotContain("Menú", "contenidoInvalido");
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

    @Test
    void shouldRejectLoopbackBeforeConnecting() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.extract("http://127.0.0.1:1/private"));

        assertThat(exception.getReason()).isEqualTo("Destino URL no permitido");
    }

    @Test
    void shouldRejectRedirectToPrivateTargetBeforeSecondRequest() throws Exception {
        TemarioUrlExtractionService.FetchedPage redirect = redirectPage("http://127.0.0.1/private");
        AtomicInteger requests = new AtomicInteger();
        TemarioUrlExtractionService redirectingService = new TemarioUrlExtractionService(
                host -> new InetAddress[]{InetAddress.getByName(
                        "public.example".equals(host) ? "93.184.216.34" : host)},
                (uri, validatedAddresses) -> {
                    requests.incrementAndGet();
                    return redirect;
                });

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                redirectingService.extract("https://public.example/start"));

        assertThat(exception.getReason()).isEqualTo("Destino URL no permitido");
        assertThat(requests).hasValue(1);
    }

    @Test
    void shouldFollowRedirectBetweenPublicTargets() throws Exception {
        TemarioUrlExtractionService.FetchedPage redirect = redirectPage("https://cdn.public.example/temario");
        TemarioUrlExtractionService.FetchedPage success = successfulPage("""
                <html><head><title>Temario público</title></head>
                <body><main>Contenido público válido para importar como temario académico.</main></body></html>
                """);
        InetAddress initialAddress = InetAddress.getByName("93.184.216.34");
        InetAddress redirectAddress = InetAddress.getByName("1.1.1.1");
        List<String> resolvedHosts = new ArrayList<>();
        List<URI> requests = new ArrayList<>();
        List<List<InetAddress>> connectionAddresses = new ArrayList<>();
        TemarioUrlExtractionService redirectingService = new TemarioUrlExtractionService(
                host -> {
                    resolvedHosts.add(host);
                    return new InetAddress[]{"public.example".equals(host) ? initialAddress : redirectAddress};
                },
                (uri, validatedAddresses) -> {
                    requests.add(uri);
                    connectionAddresses.add(List.copyOf(validatedAddresses));
                    return requests.size() == 1 ? redirect : success;
                });

        var extracted = redirectingService.extract("http://public.example/start");

        assertThat(requests).containsExactly(
                URI.create("http://public.example/start"),
                URI.create("https://cdn.public.example/temario"));
        assertThat(resolvedHosts).containsExactly("public.example", "cdn.public.example");
        assertThat(connectionAddresses).containsExactly(
                List.of(initialAddress),
                List.of(redirectAddress));
        assertThat(extracted.title()).isEqualTo("Temario público");
    }

    @Test
    void shouldPinValidatedAddressSoDnsRebindingCannotChangeConnectionTarget() throws Exception {
        InetAddress validatedPublicAddress = InetAddress.getByName("93.184.216.34");
        InetAddress reboundPrivateAddress = InetAddress.getByName("127.0.0.1");
        AtomicInteger dnsResolutions = new AtomicInteger();
        AtomicReference<List<InetAddress>> connectionAddresses = new AtomicReference<>();
        TemarioUrlExtractionService rebindingService = new TemarioUrlExtractionService(
                host -> dnsResolutions.getAndIncrement() == 0
                        ? new InetAddress[]{validatedPublicAddress}
                        : new InetAddress[]{reboundPrivateAddress},
                (uri, validatedAddresses) -> {
                    connectionAddresses.set(List.copyOf(validatedAddresses));
                    return successfulPage("""
                            <html><head><title>Temario seguro</title></head>
                            <body><main>Contenido seguro con resolución DNS fijada antes de abrir la conexión HTTP.</main></body></html>
                            """);
                });

        var extracted = rebindingService.extract("https://public.example/temario");

        assertThat(extracted.title()).isEqualTo("Temario seguro");
        assertThat(dnsResolutions).hasValue(1);
        assertThat(connectionAddresses.get()).containsExactly(validatedPublicAddress);
    }

    @Test
    void shouldRejectHostnameResolvingToPrivateAddress() throws Exception {
        TemarioUrlExtractionService privateDnsService = new TemarioUrlExtractionService(
                host -> new InetAddress[]{InetAddress.getByName("10.0.0.8")},
                (uri, validatedAddresses) -> {
                    throw new AssertionError("No debe realizarse la conexión");
                });

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                privateDnsService.extract("https://internal.example/temario"));

        assertThat(exception.getReason()).isEqualTo("Destino URL no permitido");
    }

    @Test
    void shouldRejectHostnameWhenAnyResolvedAddressIsPrivate() throws Exception {
        TemarioUrlExtractionService mixedDnsService = new TemarioUrlExtractionService(
                host -> new InetAddress[]{
                        InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("192.168.1.20")
                },
                (uri, validatedAddresses) -> {
                    throw new AssertionError("No debe realizarse la conexión");
                });

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                mixedDnsService.extract("https://mixed.example/temario"));

        assertThat(exception.getReason()).isEqualTo("Destino URL no permitido");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://127.0.0.1",
            "http://[::1]",
            "http://10.0.0.1",
            "http://172.16.0.1",
            "http://192.168.0.1",
            "http://169.254.169.254",
            "http://224.0.0.1",
            "http://0.0.0.1",
            "http://100.64.0.1",
            "http://192.0.0.1",
            "http://192.0.2.1",
            "http://192.88.99.1",
            "http://198.18.0.1",
            "http://198.51.100.1",
            "http://203.0.113.1",
            "http://240.0.0.1",
            "http://255.255.255.255",
            "http://[fc00::1]",
            "http://[fe80::1]",
            "http://[ff02::1]",
            "http://[2001::1]",
            "http://[2001:db8::1]",
            "http://[2002::1]",
            "http://[3fff::1]"
    })
    void shouldRejectNonPublicTargets(String url) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.extract(url));

        assertThat(exception.getReason()).isEqualTo("Destino URL no permitido");
    }

    private static TemarioUrlExtractionService.FetchedPage successfulPage(String html) {
        return new TemarioUrlExtractionService.FetchedPage(200, null, Jsoup.parse(html));
    }

    private static TemarioUrlExtractionService.FetchedPage redirectPage(String location) {
        return new TemarioUrlExtractionService.FetchedPage(302, location, null);
    }
}
