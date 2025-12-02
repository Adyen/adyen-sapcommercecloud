package com.adyen.commerce.repository;

import com.adyen.v6.repository.AbstractRepository;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.subscriptionservices.model.SubscriptionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class SubscriptionRepository extends AbstractRepository {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionRepository.class);
    private static final String SUBSCRIPTION_QUERY =
            "SELECT {pk} FROM {Subscription} "
                    + "WHERE {subscriptionStatus} = 'active' "
                    + "AND {nextChargeDate} >= ?startOfDay "
                    + "AND {nextChargeDate} < ?startOfNextDay "
                    + "ORDER BY {pk} ASC";

    public List<SubscriptionModel> getActiveSubscriptionByNextChargeDay() {
        LOG.debug("Querying for subscriptions");
        return executeSubscriptionQuery(
                SUBSCRIPTION_QUERY,
                Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
        );
    }

    private List<SubscriptionModel> executeSubscriptionQuery(String query, Date startOfDay, Date startOfNextDay) {
        final FlexibleSearchQuery subscriptionQuery = new FlexibleSearchQuery(query);
        subscriptionQuery.addQueryParameter("startOfDay", startOfDay);
        subscriptionQuery.addQueryParameter("startOfNextDay", startOfNextDay);

        return flexibleSearchService
                .search(subscriptionQuery)
                .getResult()
                .stream()
                .map(SubscriptionModel.class::cast)
                .collect(Collectors.toList());
    }
}
