package com.lootfast.backend.notification.event;

import com.lootfast.backend.notification.outbox.*;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.web.util.HtmlUtils;

@Component
class OrderEmailListener {
    private final EmailOutboxService outbox;
    OrderEmailListener(EmailOutboxService outbox) { this.outbox = outbox; }
    @EventListener
    public void enqueue(OrderEmailEvent event) {
        String body = "<h2>" + HtmlUtils.htmlEscape(event.title()) + "</h2><p>"
                + HtmlUtils.htmlEscape(event.message()) + "</p><p>Mở LootFast để xem đơn hàng.</p>";
        outbox.enqueue(event.notificationId(), event.email(), "LootFast — " + event.title(), body, event.category());
    }
}
