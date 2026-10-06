package com.adyen.v6.repository;

import com.adyen.v6.model.AdyenDonationModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;

import java.util.Collections;

/**
 * Finds persisted donations by the merchant reference sent to Adyen.
 */
public class AdyenDonationRepository extends AbstractRepository {

    public AdyenDonationModel findByReference(final String reference) {
        if (reference == null) {
            return null;
        }
        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {pk} FROM {" + AdyenDonationModel._TYPECODE + "}"
                        + " WHERE {" + AdyenDonationModel.REFERENCE + "} = ?reference",
                Collections.singletonMap("reference", reference));
        return (AdyenDonationModel) getOneOrNull(query);
    }
}
