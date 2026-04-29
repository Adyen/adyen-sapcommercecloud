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
package com.adyen.v6.converters;

import com.adyen.v6.dto.ExpressPaymentConfigDto;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

/**
 * Default implementation of {@link ExpressPaymentConfigConverter}.
 *
 * <p>Converts an {@code ExpressPaymentConfigModel} (Hybris generated item) to an
 * {@link ExpressPaymentConfigDto}. The source is typed as {@link ItemModel} at the interface
 * level; this implementation casts it to the generated model and reads its boolean flags.</p>
 *
 * <p>The generated model class is referenced by its fully-qualified name to keep the
 * compile-time dependency explicit while avoiding issues with IDE resolution before
 * {@code ant all} has been run.</p>
 */
public class DefaultExpressPaymentConfigConverter implements ExpressPaymentConfigConverter {

    @Override
    public ExpressPaymentConfigDto convert(final ItemModel source) throws ConversionException {
        return convert(source, new ExpressPaymentConfigDto());
    }

    @Override
    public ExpressPaymentConfigDto convert(final ItemModel source, final ExpressPaymentConfigDto prototype) throws ConversionException {
        if (source == null) {
            return prototype;
        }
        final de.hybris.platform.core.model.ItemModel raw = source;
        // Cast to the generated model — available after `ant all`
        final com.adyen.v6.model.ExpressPaymentConfigModel model =
                (com.adyen.v6.model.ExpressPaymentConfigModel) raw;

        prototype.setGooglePayExpressEnabledOnCart(Boolean.TRUE.equals(model.getGooglePayExpressEnabledOnCart()));
        prototype.setApplePayExpressEnabledOnCart(Boolean.TRUE.equals(model.getApplePayExpressEnabledOnCart()));
        prototype.setPaypalExpressEnabledOnCart(Boolean.TRUE.equals(model.getPaypalExpressEnabledOnCart()));
        prototype.setAmazonPayExpressEnabledOnCart(Boolean.TRUE.equals(model.getAmazonPayExpressEnabledOnCart()));
        prototype.setGooglePayExpressEnabledOnProduct(Boolean.TRUE.equals(model.getGooglePayExpressEnabledOnProduct()));
        prototype.setApplePayExpressEnabledOnProduct(Boolean.TRUE.equals(model.getApplePayExpressEnabledOnProduct()));
        prototype.setPaypalExpressEnabledOnProduct(Boolean.TRUE.equals(model.getPaypalExpressEnabledOnProduct()));
        prototype.setAmazonPayExpressEnabledOnProduct(Boolean.TRUE.equals(model.getAmazonPayExpressEnabledOnProduct()));

        return prototype;
    }
}
