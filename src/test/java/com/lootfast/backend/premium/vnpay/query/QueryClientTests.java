package com.lootfast.backend.premium.vnpay.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QueryClientTests {
  @Test
  void postsSignedRequestAndVerifiesRealHttpResponse() throws Exception {
    var fixture = new QueryProtocolTests();
    var calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var settings = mock(QuerySettings.class);
    when(settings.ip()).thenReturn("127.0.0.1");
    when(settings.url())
        .thenReturn(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/query"));
    var body = fixture.signed(fixture.response());
    server.createContext(
        "/query",
        exchange -> {
          assertEquals("POST", exchange.getRequestMethod());
          var request = new ObjectMapper().readTree(exchange.getRequestBody());
          assertEquals("querydr", request.get("vnp_Command").asText());
          assertEquals("20300102070000", request.get("vnp_TransactionDate").asText());
          assertEquals(
              fixture
                  .protocol
                  .request(fixture.job, QueryProtocolTests.NOW, "127.0.0.1")
                  .get("vnp_SecureHash"),
              request.get("vnp_SecureHash").asText());
          calls.incrementAndGet();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var client =
          new QueryClient(
              settings,
              fixture.protocol,
              fixture.json,
              Clock.fixed(QueryProtocolTests.NOW, ZoneOffset.UTC));
      assertEquals("VERIFIED", client.query(fixture.job).result());
      assertEquals(1, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void redirectNeverForwardsSignedPayload() throws Exception {
    var fixture = new QueryProtocolTests();
    var destinations = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var settings = mock(QuerySettings.class);
    when(settings.ip()).thenReturn("127.0.0.1");
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    when(settings.url()).thenReturn(URI.create(base + "/query"));
    server.createContext(
        "/query",
        exchange -> {
          exchange.getResponseHeaders().set("Location", base + "/other");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.createContext(
        "/other",
        exchange -> {
          destinations.incrementAndGet();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    try {
      assertEquals(
          "HTTP_ERROR",
          new QueryClient(
                  settings,
                  fixture.protocol,
                  fixture.json,
                  Clock.fixed(QueryProtocolTests.NOW, ZoneOffset.UTC))
              .query(fixture.job)
              .result());
      assertEquals(0, destinations.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void oversizedHttpResponseDoesNotReachParser() throws Exception {
    var fixture = new QueryProtocolTests();
    var protocol = mock(QueryProtocol.class);
    when(protocol.request(any(), any(), any()))
        .thenReturn(java.util.Map.of("vnp_Command", "querydr"));
    var settings = mock(QuerySettings.class);
    when(settings.ip()).thenReturn("127.0.0.1");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    when(settings.url())
        .thenReturn(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/query"));
    server.createContext(
        "/query",
        exchange -> {
          byte[] bytes = new byte[32768];
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      assertEquals(
          "TRANSPORT_ERROR",
          new QueryClient(
                  settings,
                  protocol,
                  fixture.json,
                  Clock.fixed(QueryProtocolTests.NOW, ZoneOffset.UTC))
              .query(fixture.job)
              .result());
      verify(protocol, never()).response(any(), any());
    } finally {
      server.stop(0);
    }
  }
}
