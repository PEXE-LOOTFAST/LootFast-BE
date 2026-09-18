package com.lootfast.backend.merchant.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.merchant.dto.MerchantLocationResponse;
import com.lootfast.backend.merchant.dto.MerchantOpeningHoursResponse;
import com.lootfast.backend.merchant.dto.MerchantProfileResponse;
import com.lootfast.backend.merchant.dto.OpeningHourSlotRequest;
import com.lootfast.backend.merchant.dto.OpeningHourSlotResponse;
import com.lootfast.backend.merchant.dto.UpdateMerchantLocationRequest;
import com.lootfast.backend.merchant.dto.UpdateMerchantProfileRequest;
import com.lootfast.backend.merchant.dto.UpdateOpeningHoursRequest;
import com.lootfast.backend.merchant.entity.MerchantOpeningHour;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantOpeningHourRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MerchantProfileService {

    private static final Comparator<OpeningHourSlotRequest> SLOT_ORDER =
            Comparator.comparing(OpeningHourSlotRequest::dayOfWeek)
                    .thenComparing(OpeningHourSlotRequest::opensAt);
    private static final Comparator<MerchantOpeningHour> OPENING_HOUR_ORDER =
            Comparator.comparing(MerchantOpeningHour::getDayOfWeek)
                    .thenComparing(MerchantOpeningHour::getOpensAt);

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final MerchantOpeningHourRepository openingHourRepository;

    public MerchantProfileService(
            UserRepository userRepository,
            MerchantProfileRepository merchantProfileRepository,
            MerchantOpeningHourRepository openingHourRepository
    ) {
        this.userRepository = userRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.openingHourRepository = openingHourRepository;
    }

    @Transactional(readOnly = true)
    public MerchantProfileResponse getProfile(String subject) {
        Long userId = requireMerchant(subject);
        return MerchantProfileResponse.from(findProfile(userId));
    }

    @Transactional
    public MerchantProfileResponse updateProfile(
            String subject,
            UpdateMerchantProfileRequest request
    ) {
        Long userId = requireMerchant(subject);
        MerchantProfile profile = findProfileForUpdate(userId);
        profile.updateStoreInformation(
                request.storeName().trim(),
                normalizeOptional(request.description()),
                request.businessCategory().trim(),
                request.contactEmail().trim().toLowerCase(Locale.ROOT),
                request.contactPhone().trim()
        );
        merchantProfileRepository.flush();
        return MerchantProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public MerchantLocationResponse getLocation(String subject) {
        Long userId = requireMerchant(subject);
        return MerchantLocationResponse.from(findProfile(userId));
    }

    @Transactional
    public MerchantLocationResponse updateLocation(
            String subject,
            UpdateMerchantLocationRequest request
    ) {
        Long userId = requireMerchant(subject);
        MerchantProfile profile = findProfileForUpdate(userId);
        profile.updateLocation(
                request.addressLine().trim(),
                request.ward().trim(),
                request.district().trim(),
                request.city().trim(),
                request.latitude(),
                request.longitude()
        );
        merchantProfileRepository.flush();
        return MerchantLocationResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public MerchantOpeningHoursResponse getOpeningHours(String subject) {
        Long userId = requireMerchant(subject);
        MerchantProfile profile = findProfile(userId);
        return openingHoursResponse(openingHourRepository
                .findAllByMerchantProfileId(profile.getId()));
    }

    @Transactional
    public MerchantOpeningHoursResponse updateOpeningHours(
            String subject,
            UpdateOpeningHoursRequest request
    ) {
        Long userId = requireMerchant(subject);
        MerchantProfile profile = findProfileForUpdate(userId);
        List<OpeningHourSlotRequest> slots = new ArrayList<>(request.slots());
        slots.sort(SLOT_ORDER);
        validateOpeningHours(slots);

        openingHourRepository.deleteAllByMerchantProfileId(profile.getId());
        List<MerchantOpeningHour> openingHours = slots.stream()
                .map(slot -> new MerchantOpeningHour(
                        profile,
                        slot.dayOfWeek(),
                        slot.opensAt(),
                        slot.closesAt()
                ))
                .toList();
        openingHourRepository.saveAll(openingHours);
        openingHourRepository.flush();
        return openingHoursResponse(openingHours);
    }

    private void validateOpeningHours(List<OpeningHourSlotRequest> slots) {
        Map<DayOfWeek, LocalTime> previousClosingByDay = new EnumMap<>(DayOfWeek.class);
        for (OpeningHourSlotRequest slot : slots) {
            if (!slot.opensAt().isBefore(slot.closesAt())) {
                throw invalidOpeningHours(
                        slot.dayOfWeek() + " opening time must be before closing time"
                );
            }

            LocalTime previousClosing = previousClosingByDay.get(slot.dayOfWeek());
            if (previousClosing != null && slot.opensAt().isBefore(previousClosing)) {
                throw invalidOpeningHours(
                        slot.dayOfWeek() + " opening hour slots must not overlap"
                );
            }
            previousClosingByDay.put(slot.dayOfWeek(), slot.closesAt());
        }
    }

    private MerchantOpeningHoursResponse openingHoursResponse(
            List<MerchantOpeningHour> openingHours
    ) {
        return new MerchantOpeningHoursResponse(openingHours.stream()
                .sorted(OPENING_HOUR_ORDER)
                .map(OpeningHourSlotResponse::from)
                .toList());
    }

    private Long requireMerchant(String subject) {
        Long userId = parseUserId(subject);
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(this::userNotFound);
        if (!user.hasRole(UserRole.MERCHANT)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Access is denied");
        }
        return userId;
    }

    private MerchantProfile findProfile(Long userId) {
        return merchantProfileRepository.findByUserId(userId)
                .orElseThrow(this::profileNotFound);
    }

    private MerchantProfile findProfileForUpdate(Long userId) {
        return merchantProfileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::profileNotFound);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw userNotFound();
        }
    }

    private ApiException userNotFound() {
        return new ApiException(
                ErrorCode.USER_NOT_FOUND,
                "Authenticated user is no longer available"
        );
    }

    private ApiException profileNotFound() {
        return new ApiException(
                ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                "Merchant profile has not been created"
        );
    }

    private ApiException invalidOpeningHours(String message) {
        return new ApiException(ErrorCode.INVALID_OPENING_HOURS, message);
    }
}
