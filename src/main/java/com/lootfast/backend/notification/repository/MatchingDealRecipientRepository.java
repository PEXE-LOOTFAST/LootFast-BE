package com.lootfast.backend.notification.repository;

import com.lootfast.backend.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchingDealRecipientRepository extends Repository<User, Long> {

    @Query(value = """
            with candidates as (
                select
                    recipient.id as user_id,
                    recipient.email as email,
                    coalesce(preference.email_enabled, false) as email_enabled,
                    exists (
                        select 1
                        from user_favorites favorite_store
                        where favorite_store.user_id = recipient.id
                          and favorite_store.favorite_type = 'STORE'
                          and favorite_store.merchant_profile_id = deal.merchant_profile_id
                    ) as favorite_store,
                    (
                        exists (
                            select 1
                            from user_profile_favorite_categories favorite_category
                            where favorite_category.user_profile_id = profile.id
                              and favorite_category.category_code = deal.category_code
                        )
                        or exists (
                            select 1
                            from user_favorites favorite_item
                            join deals previous_deal on previous_deal.id = favorite_item.deal_id
                            where favorite_item.user_id = recipient.id
                              and favorite_item.favorite_type = 'DEAL'
                              and previous_deal.search_name = deal.search_name
                        )
                    ) as favorite_item,
                    (
                        profile.pickup_area is not null
                        and trim(profile.pickup_area) <> ''
                        and lower(concat(
                            coalesce(store.address_line, ''), ' ',
                            coalesce(store.ward, ''), ' ',
                            coalesce(store.district, ''), ' ',
                            coalesce(store.city, '')
                        )) like concat('%', lower(trim(profile.pickup_area)), '%')
                    ) as nearby_area,
                    ((deal.original_price - deal.discounted_price) * 100
                        > deal.original_price * 50) as big_discount
                from users recipient
                join user_roles recipient_role
                  on recipient_role.user_id = recipient.id
                 and recipient_role.role = 'BUYER'
                cross join deals deal
                join merchant_profiles store on store.id = deal.merchant_profile_id
                left join user_profiles profile on profile.user_id = recipient.id
                left join notification_preferences preference
                  on preference.user_id = recipient.id
                where deal.id = :dealId
                  and recipient.status = 'VERIFIED'
                  and recipient.id <> store.user_id
                  and coalesce(preference.matching_deals_enabled, true) = true
            )
            select
                user_id as userId,
                email as email,
                email_enabled as emailEnabled,
                case
                    when favorite_store then 'FAVORITE_STORE'
                    when favorite_item then 'FAVORITE_ITEM'
                    when nearby_area then 'NEARBY_AREA'
                    else 'BIG_DISCOUNT'
                end as matchReason
            from candidates
            where favorite_store or favorite_item or nearby_area or big_discount
            order by user_id
            """, nativeQuery = true)
    List<MatchingDealRecipient> findForDeal(
            @Param("dealId") Long dealId,
            Pageable pageable
    );
}
