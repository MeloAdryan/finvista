package com.finvista.integration.contaazul;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


@Component
public class ContaAzulHttpTransport {
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private final Clock clock;
    private final Sleeper sleeper;
    private final long spacing;
    private long nextRequest;
    private final RestClient client;

    private static class Shared {
        static final ContaAzulHttpTransport INSTANCE = new ContaAzulHttpTransport(Clock.systemUTC(), 150);
    }
    static RestClient shared() { return Shared.INSTANCE.client(); }

    @Autowired
    public ContaAzulHttpTransport(Clock clock, @Value("${CONTA_AZUL_HTTP_INTERVAL_MS:150}") long spacing) {
        this(clock, Thread::sleep, Math.max(100, spacing), pooledFactory());
    }

    private static ClientHttpRequestFactory pooledFactory() {
        var pool = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(pool);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    ContaAzulHttpTransport(Clock clock, Sleeper sleeper, long spacing, ClientHttpRequestFactory factory) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.spacing = spacing;
        client = RestClient.builder().requestFactory(factory).requestInterceptor(this::intercept).build();
    }

    public RestClient client() { return client; }

    private synchronized void pace() throws IOException {
        long now = System.nanoTime();
        long wait = (Math.max(0, nextRequest - now) + 999_999) / 1_000_000;
        pause(wait);
        nextRequest = System.nanoTime() + spacing * 1_000_000;
    }

    private void pause(long millis) throws IOException {
        if (millis <= 0) return;
        try { sleeper.sleep(millis); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("Consulta Conta Azul interrompida.");
        }
    }

    ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execute) throws IOException {
        boolean retryable = request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD;
        for (int attempt = 0; ; attempt++) {
            pace();
            ClientHttpResponse response;
            try { response = execute.execute(request, body); }
            catch (IOException error) {
                if (!retryable || attempt >= 1 || Thread.currentThread().isInterrupted()) throw error;
                pause(200 + ThreadLocalRandom.current().nextInt(201));
                continue;
            }
            int status = response.getStatusCode().value();
            if (!retryable || attempt >= 1 || !(status == 429 || status == 500 || status == 502 || status == 503 || status == 504)) return response;
            Instant after = retryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), clock.instant());
            long delay = after == null ? 250 + ThreadLocalRandom.current().nextInt(251)
                    : Math.max(0, Duration.between(clock.instant(), after).toMillis());

            if (delay > 3000) return response;
            response.close();
            pause(delay);
        }
    }

    static Instant retryAfter(String value, Instant now) {
        if (value == null || value.length() > 100) return null;
        try {
            if (value.trim().matches("[0-9]{1,7}")) return now.plusSeconds(Long.parseLong(value.trim()));
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException invalid) { return null; }
    }
}
