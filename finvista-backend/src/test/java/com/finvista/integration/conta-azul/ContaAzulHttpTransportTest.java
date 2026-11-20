package com.finvista.integration.contaazul;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ContaAzulHttpTransportTest {
    final Instant now = Instant.parse("2026-11-18T12:00:00Z");
    List<Long> waits;
    RestClient client;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        waits = new ArrayList<>();
        var transport = new ContaAzulHttpTransport(Clock.fixed(now, ZoneOffset.UTC), waits::add, 0,
                org.mockito.Mockito.mock(org.springframework.http.client.ClientHttpRequestFactory.class));
        var builder = RestClient.builder().requestInterceptor(transport::intercept);
        server = MockRestServiceServer.bindTo(builder).build(); client = builder.build();
    }
    @Test void retriesReadOnceAfter503() {
        server.expect(requestTo("https://example.test/read")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://example.test/read")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        assertEquals("ok", client.get().uri("https://example.test/read").retrieve().body(String.class));
        assertEquals(1, waits.size()); server.verify();
    }
    @Test void timeoutRetriesWithoutChangingRequest() {
        server.expect(requestTo("https://example.test/read")).andRespond(request -> { throw new java.net.SocketTimeoutException("test"); });
        server.expect(requestTo("https://example.test/read")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        assertEquals("ok", client.get().uri("https://example.test/read").retrieve().body(String.class)); server.verify();
    }
    @Test void refreshPostIsNeverRetriedAutomatically() {
        server.expect(requestTo("https://example.test/token")).andExpect(method(HttpMethod.POST)).andRespond(withServerError());
        assertThrows(RestClientResponseException.class, () -> client.post().uri("https://example.test/token").retrieve().body(String.class));
        assertTrue(waits.isEmpty()); server.verify();
    }
    @Test void shortRetryAfterIsRespected() {
        server.expect(requestTo("https://example.test/read")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "2"));
        server.expect(requestTo("https://example.test/read")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        assertEquals("ok", client.get().uri("https://example.test/read").retrieve().body(String.class));
        assertEquals(List.of(2000L), waits); server.verify();
    }
    @Test void longRetryAfterReturnsToSchedulerWithoutSleepingWorker() {
        server.expect(requestTo("https://example.test/read")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60"));
        var e = assertThrows(RestClientResponseException.class, () -> client.get().uri("https://example.test/read").retrieve().body(String.class));
        var safe = ContaAzulAutoDiagnostic.causaSegura(e);
        assertNotNull(ContaAzulAutoDiagnostic.retryAfter(safe)); assertTrue(waits.isEmpty()); server.verify();
    }
    @Test void authErrorsAreNotTransportRetries() {
        server.expect(requestTo("https://example.test/read")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(RestClientResponseException.class, () -> client.get().uri("https://example.test/read").retrieve().body(String.class));
        assertTrue(waits.isEmpty()); server.verify();
    }
    @Test void supportsHttpDateAndIgnoresInvalidHeaders() {
        assertEquals(Instant.parse("2026-11-18T12:01:00Z"), ContaAzulHttpTransport.retryAfter("Wed, 18 Nov 2026 12:01:00 GMT", now));
        assertNull(ContaAzulHttpTransport.retryAfter("secret-not-a-date", now));
    }
    @Test void authorizationHeadersRemainPerRequest() {
        server.expect(requestTo("https://example.test/a")).andExpect(header("Authorization", "Bearer company-a")).andRespond(withSuccess("a", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://example.test/b")).andExpect(header("Authorization", "Bearer company-b")).andRespond(withSuccess("b", MediaType.TEXT_PLAIN));
        client.get().uri("https://example.test/a").headers(h -> h.setBearerAuth("company-a")).retrieve().body(String.class);
        client.get().uri("https://example.test/b").headers(h -> h.setBearerAuth("company-b")).retrieve().body(String.class);
        server.verify();
    }
}
