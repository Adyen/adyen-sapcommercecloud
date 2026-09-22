package com.adyen.v6.repository;

import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.SearchResult;
import org.apache.log4j.Logger;

public class AdyenPartialPaymentOrderRepository extends AbstractRepository {

    private static final Logger LOG = Logger.getLogger(OrderRepository.class);

    public AdyenPartialPaymentOrderModel findPartialPaymentOrderByPspReference(String pspReference) {
        try {
            FlexibleSearchQuery searchQuery = new FlexibleSearchQuery(
                    "SELECT {" + AdyenPartialPaymentOrderModel.PK + "} FROM {" + AdyenPartialPaymentOrderModel._TYPECODE + "}" +
                    " WHERE {"+ AdyenPartialPaymentOrderModel.PSPREFERENCE +"} = ?pspReference");
            searchQuery.addQueryParameter("pspReference", pspReference);

            SearchResult<AdyenPartialPaymentOrderModel> result = getFlexibleSearchService().search(searchQuery);
            if (result.getCount() > 0) {
                return result.getResult().get(0);
            }
        } catch (Exception e) {
            LOG.error("Error finding partial payment order by PSP reference: " + pspReference, e);
        }
        return null;
    }
}
