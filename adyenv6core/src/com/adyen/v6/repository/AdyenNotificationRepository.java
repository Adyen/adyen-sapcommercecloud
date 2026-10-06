package com.adyen.v6.repository;

import com.adyen.v6.model.AdyenNotificationModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;

import java.util.HashMap;
import java.util.Map;

public class AdyenNotificationRepository extends AbstractRepository {

    public boolean isProcessed(final String pspReference, final String eventCode, final boolean success) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("pspReference", pspReference);
        parameters.put("eventCode", eventCode);
        parameters.put("success", success);

        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {pk} FROM {" + AdyenNotificationModel._TYPECODE + "}"
                        + " WHERE {" + AdyenNotificationModel.PSPREFERENCE + "} = ?pspReference"
                        + " AND {" + AdyenNotificationModel.EVENTCODE + "} = ?eventCode"
                        + " AND {" + AdyenNotificationModel.SUCCESS + "} = ?success"
                        + " AND {" + AdyenNotificationModel.PROCESSEDAT + "} IS NOT NULL",
                parameters);
        query.setCount(1);
        return !getFlexibleSearchService().search(query).getResult().isEmpty();
    }
}
