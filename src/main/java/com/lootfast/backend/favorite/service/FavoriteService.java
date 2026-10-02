package com.lootfast.backend.favorite.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealResponseMapper;
import com.lootfast.backend.favorite.dto.FavoriteItemResponse;
import com.lootfast.backend.favorite.entity.FavoriteType;
import com.lootfast.backend.favorite.entity.UserFavorite;
import com.lootfast.backend.favorite.repository.UserFavoriteRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class FavoriteService {

    private static final int FREE_FAVORITE_LIMIT = 3;
    private static final Set<DealStatus> FAVORITABLE_DEAL_STATUSES = Set.of(
            DealStatus.ACTIVE,
            DealStatus.SOLD_OUT
    );

    private final UserRepository userRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final DealRepository dealRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final DealResponseMapper dealResponseMapper;
    private final Clock clock;

    public FavoriteService(
            UserRepository userRepository,
            UserFavoriteRepository favoriteRepository,
            DealRepository dealRepository,
            MerchantProfileRepository merchantProfileRepository,
            DealResponseMapper dealResponseMapper,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.favoriteRepository = favoriteRepository;
        this.dealRepository = dealRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.dealResponseMapper = dealResponseMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<FavoriteItemResponse> getFavorites(
            String subject,
            int page,
            int size
    ) {
        Long userId = requireUserId(subject);
        if (!userRepository.existsById(userId)) {
            throw userNotFound();
        }
        Page<FavoriteItemResponse> favorites = favoriteRepository.findPageByUserId(
                        userId,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by("createdAt").descending()
                                        .and(Sort.by("id").descending())
                        )
                )
                .map(this::toResponse);
        return PagedResponse.from(favorites);
    }

    @Transactional
    public FavoriteItemResponse saveDeal(String subject, UUID dealId) {
        User user = lockUser(subject);
        var existing = favoriteRepository.findDealFavorite(user.getId(), dealId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        Deal deal = dealRepository.findPublicDeal(
                        dealId,
                        FAVORITABLE_DEAL_STATUSES,
                        clock.instant()
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.DEAL_NOT_FOUND,
                        "Deal is not available"
                ));
        enforceFreeLimit(user.getId());
        UserFavorite favorite = favoriteRepository.saveAndFlush(UserFavorite.deal(user, deal));
        return toResponse(favorite);
    }

    @Transactional
    public FavoriteItemResponse saveStore(String subject, UUID merchantId) {
        User user = lockUser(subject);
        var existing = favoriteRepository.findStoreFavorite(
                user.getId(),
                merchantId
        );
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        MerchantProfile store = merchantProfileRepository.findPublicByUserPublicId(merchantId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                        "Store is not available"
                ));
        enforceFreeLimit(user.getId());
        UserFavorite favorite = favoriteRepository.saveAndFlush(UserFavorite.store(user, store));
        return toResponse(favorite);
    }

    @Transactional
    public void removeDeal(String subject, UUID dealId) {
        User user = lockUser(subject);
        favoriteRepository.deleteByUserIdAndDealPublicId(user.getId(), dealId);
    }

    @Transactional
    public void removeStore(String subject, UUID merchantId) {
        User user = lockUser(subject);
        favoriteRepository.deleteByUserIdAndMerchantProfileUserPublicId(
                user.getId(),
                merchantId
        );
    }

    private FavoriteItemResponse toResponse(UserFavorite favorite) {
        if (favorite.getDeal() != null) {
            return dealResponse(favorite.getDeal(), favorite.getCreatedAt());
        }
        return storeResponse(favorite.getMerchantProfile(), favorite.getCreatedAt());
    }

    private FavoriteItemResponse dealResponse(Deal deal, Instant savedAt) {
        return new FavoriteItemResponse(
                FavoriteType.DEAL,
                savedAt,
                dealResponseMapper.toDetail(deal, null),
                null
        );
    }

    private FavoriteItemResponse storeResponse(
            MerchantProfile store,
            Instant savedAt
    ) {
        return new FavoriteItemResponse(
                FavoriteType.STORE,
                savedAt,
                null,
                new FavoriteItemResponse.StoreSummary(
                        store.getUser().getPublicId(),
                        store.getStoreName(),
                        store.getLogoUrl(),
                        store.getAddressLine(),
                        store.getWard(),
                        store.getDistrict(),
                        store.getCity(),
                        store.isVerified()
                )
        );
    }

    private void enforceFreeLimit(Long userId) {
        if (favoriteRepository.countByUserId(userId) >= FREE_FAVORITE_LIMIT) {
            throw new ApiException(
                    ErrorCode.FAVORITE_LIMIT_REACHED,
                    "Free accounts can save at most 3 favorites"
            );
        }
    }

    private User lockUser(String subject) {
        return userRepository.findByIdForUpdate(requireUserId(subject))
                .orElseThrow(this::userNotFound);
    }

    private Long requireUserId(String subject) {
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
}
