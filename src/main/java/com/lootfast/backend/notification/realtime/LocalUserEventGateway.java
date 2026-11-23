package com.lootfast.backend.notification.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Single-instance gateway (also used by tests): no cross-instance fan-out. */
@Component
@ConditionalOnProperty(name = "lootfast.realtime.relay", havingValue = "local")
public class LocalUserEventGateway implements UserEventGateway {

    private final LocalUserEventDelivery delivery;

    public LocalUserEventGateway(LocalUserEventDelivery delivery) {
        this.delivery = delivery;
    }

    @Override
    public void send(Long userId, RealtimeEnvelope envelope) {
        delivery.deliver(userId.toString(), envelope);
    }
}
