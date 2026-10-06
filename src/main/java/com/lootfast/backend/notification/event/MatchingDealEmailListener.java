package com.lootfast.backend.notification.event;

import com.lootfast.backend.notification.outbox.*;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.web.util.HtmlUtils;
import java.text.NumberFormat;
import java.util.Locale;

@Component
class MatchingDealEmailListener {
    private final EmailOutboxService outbox;
    MatchingDealEmailListener(EmailOutboxService outbox) { this.outbox = outbox; }
    @EventListener
    public void enqueue(MatchingDealEmailEvent event) {
        var money = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        String body = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto">
                  <h2>Deal phù hợp dành cho bạn</h2>
                  <p><strong>%s</strong> tại <strong>%s</strong> vừa được đăng trên LootFast.</p>
                  <p><span style="text-decoration:line-through;color:#888">%s ₫</span>
                     <strong style="color:#e74c3c;font-size:20px">%s ₫</strong></p>
                  <p>Mở LootFast để xem thời gian nhận món và số lượng còn lại.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(event.dealName()), HtmlUtils.htmlEscape(event.storeName()),
                        money.format(event.originalPrice()), money.format(event.discountedPrice()));
        outbox.enqueue(event.notificationId(), event.recipientEmail(), "LootFast — Deal phù hợp dành cho bạn",
                body, EmailPreferenceCategory.MATCHING_DEALS);
    }
}
