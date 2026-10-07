package com.lootfast.backend.premium.vnpay.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class QueryClient {
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final QuerySettings settings;
  private final QueryProtocol protocol;
  private final ObjectMapper json;
  private final Clock clock;

  public QueryClient(
      QuerySettings settings, QueryProtocol protocol, ObjectMapper json, Clock clock) {
    this.settings = settings;
    this.protocol = protocol;
    this.json = json;
    this.clock = clock;
  }

  public QueryResult query(QueryJob job) {
    settings.requireEnabled();
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Do not call VNPay while holding a database transaction");
    CompletableFuture<HttpResponse<byte[]>> future = null;
    try {
      var request =
          HttpRequest.newBuilder(settings.url())
              .timeout(Duration.ofSeconds(8))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofByteArray(
                      json.writeValueAsBytes(
                          protocol.request(job, clock.instant(), settings.ip()))))
              .build();
      future = http.sendAsync(request, ignored -> new LimitedBody());
      var response = future.get(8, TimeUnit.SECONDS);
      if (response.statusCode() != 200) return QueryResult.retry("HTTP_ERROR");
      return protocol.response(job, response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return QueryResult.retry("INTERRUPTED");
    } catch (Exception e) {
      return QueryResult.retry("TRANSPORT_ERROR");
    } finally {
      if (future != null && !future.isDone()) future.cancel(true);
    }
  }

  // Cancel transport before buffering an unbounded provider response.
  static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    final CompletableFuture<byte[]> result = new CompletableFuture<>();
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Flow.Subscription subscription;

    public CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(Flow.Subscription s) {
      subscription = s;
      s.request(1);
    }

    public void onNext(List<ByteBuffer> chunks) {
      for (var chunk : chunks) {
        if (buffer.size() + chunk.remaining() > 16384) {
          subscription.cancel();
          result.completeExceptionally(new IllegalStateException("Response limit"));
          return;
        }
        var bytes = new byte[chunk.remaining()];
        chunk.get(bytes);
        buffer.writeBytes(bytes);
      }
      subscription.request(1);
    }

    public void onError(Throwable t) {
      result.completeExceptionally(t);
    }

    public void onComplete() {
      result.complete(buffer.toByteArray());
    }
  }
}
