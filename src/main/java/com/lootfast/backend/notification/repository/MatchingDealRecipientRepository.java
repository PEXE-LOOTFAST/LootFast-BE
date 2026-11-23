package com.lootfast.backend.notification.repository;

import com.lootfast.backend.user.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MatchingDealRecipientRepository extends Repository<User, Long> {

    /**
     * Returns the next page of buyers (ordered by id, strictly after {@code afterUserId}) that have
     * at least one preference signal for the deal. Scoring and the daily cap are applied in Java
     * so weights stay configurable and unit-testable.
     */
    @Query(value = """
            with deal_ctx as (
                select
                    deal.merchant_profile_id,
                    deal.category_code,
                    deal.search_name,
                    deal.original_price,
                    deal.discounted_price,
                    store.user_id as store_user_id,
                    lower(concat(
                        coalesce(store.address_line, ''), ' ',
                        coalesce(store.ward, ''), ' ',
                        coalesce(store.district, ''), ' ',
                        coalesce(store.city, '')
                    )) as store_address
                from deals deal
                join merchant_profiles store on store.id = deal.merchant_profile_id
                where deal.id = :dealId
            ),
            candidates as (
                select
                    recipient.id as user_id,
                    recipient.email as email,
                    coalesce(preference.email_enabled, false) as email_enabled,
                    exists (
                        select 1
                        from user_favorites favorite_store
                        where favorite_store.user_id = recipient.id
                          and favorite_store.favorite_type = 'STORE'
                          and favorite_store.merchant_profile_id = d.merchant_profile_id
                    ) as favorite_store,
                    exists (
                        select 1
                        from user_profile_favorite_categories favorite_category
                        where favorite_category.user_profile_id = profile.id
                          and favorite_category.category_code = d.category_code
                    ) as favorite_category,
                    exists (
                        select 1
                        from user_favorites favorite_item
                        join deals previous_deal on previous_deal.id = favorite_item.deal_id
                        where favorite_item.user_id = recipient.id
                          and favorite_item.favorite_type = 'DEAL'
                          and previous_deal.search_name = d.search_name
                    ) as favorite_item,
                    (
                        select count(*)
                        from customer_orders past_order
                        join deals ordered_deal on ordered_deal.id = past_order.deal_id
                        where past_order.buyer_id = recipient.id
                          and past_order.status = 'COMPLETED'
                          and past_order.created_at >= :orderHistorySince
                          and ordered_deal.category_code = d.category_code
                    ) >= :orderHistoryMinOrders as order_history,
                    (
                        profile.pickup_area is not null
                        and trim(profile.pickup_area) <> ''
                        and d.store_address like concat('%', lower(trim(profile.pickup_area)), '%')
                    ) as nearby_area,
                    ((d.original_price - d.discounted_price) * 100
                        > d.original_price * 50) as big_discount
                from users recipient
                join user_roles recipient_role
                  on recipient_role.user_id = recipient.id
                 and recipient_role.role = 'BUYER'
                cross join deal_ctx d
                left join user_profiles profile on profile.user_id = recipient.id
                left join notification_preferences preference
                  on preference.user_id = recipient.id
                where recipient.id > :afterUserId
                  and recipient.status = 'VERIFIED'
                  and recipient.locked_at is null
                  and recipient.id <> d.store_user_id
                  and coalesce(preference.matching_deals_enabled, true) = true
            )
            select
                c.user_id as userId,
                c.email as email,
                c.email_enabled as emailEnabled,
                c.favorite_store as favoriteStore,
                c.favorite_category as favoriteCategory,
                c.favorite_item as favoriteItem,
                c.order_history as orderHistory,
                c.nearby_area as nearbyArea,
                c.big_discount as bigDiscount,
                (
                    select count(*)
                    from notifications sent
                    where sent.recipient_user_id = c.user_id
                      and sent.type = 'MATCHING_DEAL'
                      and sent.created_at >= :dayStart
                ) as sentToday
            from candidates c
            where c.favorite_store or c.favorite_category or c.favorite_item
               or c.order_history or c.nearby_area or c.big_discount
            order by c.user_id
            limit :batchSize
            """, nativeQuery = true)
    List<MatchingDealRecipient> findCandidates(
            @Param("dealId") Long dealId,
            @Param("afterUserId") long afterUserId,
            @Param("orderHistorySince") Instant orderHistorySince,
            @Param("orderHistoryMinOrders") int orderHistoryMinOrders,
            @Param("dayStart") Instant dayStart,
            @Param("batchSize") int batchSize
    );
}
