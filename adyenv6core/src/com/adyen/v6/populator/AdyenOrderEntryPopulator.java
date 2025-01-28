/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.populator;

import javax.annotation.Nonnull;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.commercefacades.order.converters.populator.OrderEntryPopulator;

/**
 * Populate TaxValues into the OrderEntryData object.
 * This data is needed for OpenInvoice Payment Methods
 */
public class AdyenOrderEntryPopulator extends OrderEntryPopulator {

    @Override
    public void populate(@Nonnull final AbstractOrderEntryModel source, @Nonnull final OrderEntryData target)
    {
        target.setTaxValues(source.getTaxValues());
        if (source.getUnit() != null) {
            target.setUnitOfMeasure(source.getUnit().getName());
        }
    }
}
