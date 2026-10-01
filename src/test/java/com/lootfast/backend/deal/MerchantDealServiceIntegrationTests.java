package com.lootfast.backend.deal;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.deal.dto.CreateMerchantDealRequest;
import com.lootfast.backend.deal.dto.DealTemplateResponse;
import com.lootfast.backend.deal.dto.MerchantDealResponse;
import com.lootfast.backend.deal.dto.RemoveDealRequest;
import com.lootfast.backend.deal.dto.ReuseDealTemplateRequest;
import com.lootfast.backend.deal.dto.UpdateDealQuantityRequest;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealImageRepository;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.repository.DealTemplateRepository;
import com.lootfast.backend.deal.service.DealImageService;
import com.lootfast.backend.deal.service.MerchantDealService;
import com.lootfast.backend.deal.service.DealTemplateService;
import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.merchant.entity.MerchantOpeningHour;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantOpeningHourRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.service.MerchantImageUploadRateLimiter;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class MerchantDealServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant SALE_START = Instant.parse("2030-01-02T01:00:00Z");
    private static final Instant SALE_END = Instant.parse("2030-01-02T08:00:00Z");
    private static final Instant PICKUP_START = Instant.parse("2030-01-02T09:00:00Z");
    private static final Instant PICKUP_END = Instant.parse("2030-01-02T10:00:00Z");

    @Autowired private MerchantDealService dealService;
    @Autowired private DealTemplateService templateService;
    @Autowired private DealImageService imageService;
    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantRepository;
    @Autowired private MerchantOpeningHourRepository openingHourRepository;
    @Autowired private MerchantApplicationRepository applicationRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private DealImageRepository imageRepository;
    @Autowired private DealTemplateRepository templateRepository;
    @Autowired private OrderRepository orderRepository;

    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private MerchantImageUploadRateLimiter uploadRateLimiter;
    @MockitoBean private Clock clock;

    private User merchant;
    private User admin;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        orderRepository.deleteAll();
        templateRepository.deleteAll();
        imageRepository.deleteAll();
        dealRepository.deleteAll();
        openingHourRepository.deleteAll();
        applicationRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();

        merchant = userRepository.save(new User(
                "Merchant",
                "Approved",
                "approved-merchant@lootfast.local",
                "911111111",
                "encoded",
                UserRole.MERCHANT,
                UserStatus.VERIFIED
        ));
        admin = userRepository.save(new User(
                "Admin",
                "Reviewer",
                "deal-admin@lootfast.local",
                "922222222",
                "encoded",
                UserRole.ADMIN,
                UserStatus.VERIFIED
        ));

        MerchantProfile profile = new MerchantProfile(merchant);
        profile.updateStoreInformation(
                "Evening Bakery",
                "Fresh food",
                "BAKERY",
                "store@lootfast.local",
                "+84912345678"
        );
        profile.updateLocation(
                "1 Test Street",
                "Ben Nghe",
                "District 1",
                "Ho Chi Minh City",
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009)
        );
        merchantRepository.save(profile);
        DayOfWeek pickupDay = PICKUP_START.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).getDayOfWeek();
        openingHourRepository.save(new MerchantOpeningHour(
                profile,
                pickupDay,
                LocalTime.of(7, 0),
                LocalTime.of(20, 0)
        ));

        MerchantApplication application = new MerchantApplication(
                merchant,
                "03123456789012",
                UUID.randomUUID()
        );
        application.markPending("licenses/test", "PNG", NOW.minusSeconds(3600));
        application.approve(admin, NOW.minusSeconds(1800));
        applicationRepository.save(application);

        when(imageStorage.upload(any(), any(), any()))
                .thenAnswer(invocation -> new StoredImage(
                        invocation.getArgument(0),
                        "https://cdn.lootfast.test/" + UUID.randomUUID() + ".png"
                ));
    }

    @Test
    void approvedMerchantCanCreateUploadPublishAndListDeal() throws Exception {
        MerchantDealResponse draft = dealService.create(subject(), validRequest("Bread combo"));
        assertEquals(DealStatus.DRAFT, draft.status());
        assertEquals(10, draft.totalQuantity());

        MerchantDealResponse withImage = imageService.upload(
                subject(),
                draft.dealId(),
                validPng()
        );
        assertEquals(1, withImage.images().size());

        MerchantDealResponse published = dealService.publish(subject(), draft.dealId());
        assertEquals(DealStatus.ACTIVE, published.status());
        assertEquals(withImage.images().getFirst().imageUrl(), published.primaryImageUrl());

        var page = dealService.list(subject(), 0, 20);
        assertEquals(1, page.totalElements());
        assertEquals(draft.dealId(), page.items().getFirst().dealId());
    }

    @Test
    void merchantWithoutApprovedApplicationCannotCreateDeal() {
        applicationRepository.deleteAll();
        ApiException exception = assertThrows(
                ApiException.class,
                () -> dealService.create(subject(), validRequest("Blocked deal"))
        );
        assertEquals(ErrorCode.MERCHANT_NOT_APPROVED, exception.getErrorCode());
    }

    @Test
    void publishedDealsForSameDayMustShareSchedule() throws Exception {
        MerchantDealResponse first = createWithImage(validRequest("First deal"));
        dealService.publish(subject(), first.dealId());

        CreateMerchantDealRequest conflicting = new CreateMerchantDealRequest(
                "Second deal",
                "BAKERY",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                10,
                10,
                SALE_START.plusSeconds(1800),
                SALE_END,
                PICKUP_START,
                PICKUP_END
        );
        MerchantDealResponse second = createWithImage(conflicting);
        ApiException exception = assertThrows(
                ApiException.class,
                () -> dealService.publish(subject(), second.dealId())
        );
        assertEquals(ErrorCode.STORE_SCHEDULE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void quantityCannotDropBelowAlreadyCommittedPortions() throws Exception {
        MerchantDealResponse draft = createWithImage(validRequest("Limited deal"));
        MerchantDealResponse published = dealService.publish(subject(), draft.dealId());
        Deal deal = dealRepository.findByPublicId(published.dealId()).orElseThrow();
        deal.reserve(4);
        dealRepository.saveAndFlush(deal);
        long currentVersion = dealRepository.findByPublicId(published.dealId())
                .orElseThrow()
                .getVersion();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> dealService.updateQuantity(
                        subject(),
                        published.dealId(),
                        new UpdateDealQuantityRequest(3, currentVersion)
                )
        );
        assertEquals(ErrorCode.DEAL_QUANTITY_CONFLICT, exception.getErrorCode());

        MerchantDealResponse updated = dealService.updateQuantity(
                subject(),
                published.dealId(),
                new UpdateDealQuantityRequest(8, currentVersion)
        );
        assertEquals(8, updated.totalQuantity());
        assertEquals(4, updated.remainingQuantity());
        assertEquals(4, updated.committedQuantity());
    }

    @Test
    void discountMustBeAtLeastTenPercent() {
        CreateMerchantDealRequest invalid = new CreateMerchantDealRequest(
                "Small discount",
                "BAKERY",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(95_000),
                10,
                10,
                SALE_START,
                SALE_END,
                PICKUP_START,
                PICKUP_END
        );
        ApiException exception = assertThrows(
                ApiException.class,
                () -> dealService.create(subject(), invalid)
        );
        assertEquals(ErrorCode.INVALID_DEAL_PRICE, exception.getErrorCode());
    }

    @Test
    void adminRemovingOnlyPublishedImageHidesDeal() throws Exception {
        MerchantDealResponse draft = createWithImage(validRequest("Moderated deal"));
        MerchantDealResponse published = dealService.publish(subject(), draft.dealId());

        MerchantDealResponse moderated = imageService.removeByAdmin(
                admin.getId().toString(),
                published.images().getFirst().imageId(),
                "Image does not show the actual food"
        );

        assertEquals(DealStatus.HIDDEN, moderated.status());
        assertEquals(0, moderated.images().size());
        assertNull(moderated.primaryImageUrl());
    }

    @Test
    void merchantCanSaveAndReuseDealTemplateAsIndependentDraft() {
        MerchantDealResponse source = dealService.create(subject(), validRequest("Reusable bread"));

        DealTemplateResponse template = templateService.save(subject(), source.dealId());
        MerchantDealResponse reused = templateService.reuse(
                subject(),
                template.templateId(),
                new ReuseDealTemplateRequest(
                        7,
                        Instant.parse("2030-01-03T01:00:00Z"),
                        Instant.parse("2030-01-03T08:00:00Z"),
                        Instant.parse("2030-01-03T09:00:00Z"),
                        Instant.parse("2030-01-03T10:00:00Z")
                )
        );

        assertNotEquals(source.dealId(), reused.dealId());
        assertEquals(DealStatus.DRAFT, reused.status());
        assertEquals(source.name(), reused.name());
        assertEquals(source.originalPrice(), reused.originalPrice());
        assertEquals(source.discountedPrice(), reused.discountedPrice());
        assertEquals(7, reused.totalQuantity());
        assertEquals(0, reused.images().size());
        assertEquals(1, templateService.list(subject(), 0, 20).totalElements());

        templateService.delete(subject(), template.templateId());
        assertEquals(0, templateService.list(subject(), 0, 20).totalElements());
    }

    @Test
    void hidingRestoringAndRemovingDealPreservesExistingOrder() throws Exception {
        MerchantDealResponse draft = createWithImage(validRequest("Reserved bread"));
        MerchantDealResponse published = dealService.publish(subject(), draft.dealId());
        Deal deal = dealRepository.findByPublicId(published.dealId()).orElseThrow();
        deal.reserve(2);
        dealRepository.saveAndFlush(deal);

        User buyer = userRepository.save(new User(
                "Buyer",
                "Customer",
                "deal-buyer@lootfast.local",
                "933333333",
                "encoded",
                UserRole.BUYER,
                UserStatus.VERIFIED
        ));
        CustomerOrder order = orderRepository.saveAndFlush(new CustomerOrder(
                buyer,
                deal,
                2,
                PaymentMethod.CASH,
                UUID.randomUUID(),
                "deal-lifecycle-test",
                NOW
        ));

        MerchantDealResponse hidden = dealService.hide(subject(), published.dealId());
        assertEquals(DealStatus.HIDDEN, hidden.status());
        assertEquals(NOW, hidden.hiddenAt());
        assertEquals(OrderStatus.RESERVED, orderRepository.findById(order.getId()).orElseThrow().getStatus());

        MerchantDealResponse restored = dealService.restore(subject(), published.dealId());
        assertEquals(DealStatus.ACTIVE, restored.status());
        assertNull(restored.hiddenAt());

        MerchantDealResponse removed = dealService.remove(
                subject(),
                published.dealId(),
                new RemoveDealRequest("Sold out unexpectedly")
        );
        assertEquals(DealStatus.REMOVED, removed.status());
        assertEquals(NOW, removed.removedAt());
        assertEquals("Sold out unexpectedly", removed.removalReason());
        assertNotNull(dealRepository.findByPublicId(published.dealId()).orElseThrow());
        assertEquals(OrderStatus.RESERVED, orderRepository.findById(order.getId()).orElseThrow().getStatus());

        ApiException restoreRemoved = assertThrows(
                ApiException.class,
                () -> dealService.restore(subject(), published.dealId())
        );
        assertEquals(ErrorCode.DEAL_NOT_RESTORABLE, restoreRemoved.getErrorCode());
    }

    private MerchantDealResponse createWithImage(CreateMerchantDealRequest request) throws Exception {
        MerchantDealResponse draft = dealService.create(subject(), request);
        return imageService.upload(subject(), draft.dealId(), validPng());
    }

    private CreateMerchantDealRequest validRequest(String name) {
        return new CreateMerchantDealRequest(
                name,
                "BAKERY",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                10,
                10,
                SALE_START,
                SALE_END,
                PICKUP_START,
                PICKUP_END
        );
    }

    private MockMultipartFile validPng() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "deal.png", "image/png", output.toByteArray());
    }

    private String subject() {
        return merchant.getId().toString();
    }

}
