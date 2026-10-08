package com.lootfast.backend.notification.service;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.notification.entity.DealMatchReason;
import com.lootfast.backend.notification.entity.NotificationType;
import com.lootfast.backend.notification.entity.UserNotification;
import com.lootfast.backend.notification.event.MatchingDealEmailEvent;
import com.lootfast.backend.notification.repository.MatchingDealRecipient;
import com.lootfast.backend.notification.repository.MatchingDealRecipientRepository;
import com.lootfast.backend.notification.repository.UserNotificationRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MatchingDealNotificationService {

    private static final int RECIPIENT_BATCH_SIZE = 500;
    private static final String TITLE = "Deal phù hợp dành cho bạn";

    private final MatchingDealRecipientRepository recipientRepository;
    private final UserNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MatchingDealNotificationService(
            MatchingDealRecipientRepository recipientRepository,
            UserNotificationRepository notificationRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.recipientRepository = recipientRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createForPublishedDeal(Deal deal) {
        int page = 0;
        while (true) {
            List<MatchingDealRecipient> recipients = recipientRepository.findForDeal(
                    deal.getId(),
                    PageRequest.of(page, RECIPIENT_BATCH_SIZE)
            );
            if (recipients.isEmpty()) {
                return;
            }

            createBatch(deal, recipients);
            if (recipients.size() < RECIPIENT_BATCH_SIZE) {
                return;
            }
            page++;
        }
    }

    private void createBatch(Deal deal, List<MatchingDealRecipient> recipients) {
        List<Long> recipientIds = recipients.stream().map(MatchingDealRecipient::getUserId).toList();
        Set<Long> existingRecipientIds = new HashSet<>(notificationRepository.findExistingRecipientIds(
                NotificationType.MATCHING_DEAL,
                deal.getPublicId(),
                recipientIds
        ));

        var recipientUsers = userRepository.findAllById(recipientIds).stream()
                .collect(java.util.stream.Collectors.toMap(user -> user.getId(), user -> user));

        for (MatchingDealRecipient recipient : recipients) {
            if (existingRecipientIds.contains(recipient.getUserId())) {
                continue;
            }
            var user = recipientUsers.get(recipient.getUserId());
            if (user == null) {
                continue;
            }

            DealMatchReason reason = DealMatchReason.valueOf(recipient.getMatchReason());
            UserNotification notification = notificationRepository.save(new UserNotification(
                    user,
                    reason,
                    deal.getPublicId(),
                    deal.getMerchant().getUser().getPublicId(),
                    TITLE,
                    message(reason, deal),
                    deal.getPrimaryImageUrl()
            ));

            if (recipient.getEmailEnabled()) {
                eventPublisher.publishEvent(new MatchingDealEmailEvent(
                        recipient.getEmail(),
                        notification.getPublicId(),
                        deal.getName(),
                        deal.getMerchant().getStoreName(),
                        deal.getOriginalPrice(),
                        deal.getDiscountedPrice()
                ));
            }
        }
    }

    private String message(DealMatchReason reason, Deal deal) {
        String storeName = deal.getMerchant().getStoreName();
        return switch (reason) {
            case FAVORITE_STORE -> "%s vừa đăng deal %s.".formatted(storeName, deal.getName());
            case FAVORITE_ITEM -> "%s bạn quan tâm vừa có deal mới tại %s."
                    .formatted(deal.getName(), storeName);
            case NEARBY_AREA -> "Có deal %s gần khu vực bạn theo dõi tại %s."
                    .formatted(deal.getName(), storeName);
            case SAVED_FILTER -> "Deal %s phù hợp bộ lọc đã lưu của bạn tại %s.".formatted(deal.getName(),storeName);
            case BIG_DISCOUNT -> "%s đang giảm hơn 50%% tại %s."
                    .formatted(deal.getName(), storeName);
        };
    }
}
