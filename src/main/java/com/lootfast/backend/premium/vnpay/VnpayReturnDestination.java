package com.lootfast.backend.premium.vnpay;

import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnpayReturnDestination {
  private final String url;

  public VnpayReturnDestination(@Value("${lootfast.vnpay.fe-result-url:}") String url) {
    this.url = url;
    if (!url.isBlank()) {
      try {
        var u = URI.create(url);
        boolean local =
            "http".equals(u.getScheme())
                && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(u.getHost());
        if (!(local || "https".equals(u.getScheme()))
            || u.getHost() == null
            || u.getUserInfo() != null
            || u.getQuery() != null
            || u.getFragment() != null
            || url.length() > 2048
            || url.chars().anyMatch(c -> c < 32 || c > 126)) throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "VNPAY_FE_RESULT_URL must be a fixed HTTPS URL (HTTP loopback allowed locally) without"
                + " query/fragment");
      }
    }
  }

  public URI result(UUID order, boolean valid) {
    return url.isBlank()
        ? null
        : URI.create(url + (valid ? "?orderId=" + order : "?error=invalid_callback"));
  }
}
