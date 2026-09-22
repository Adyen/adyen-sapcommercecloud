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
import de.hybris.platform.servicelayer.dto.converter.Converter;

/**
 * Converts an {@code ExpressPaymentConfigModel} (Hybris generated item) to an
 * {@link ExpressPaymentConfigDto} data-transfer object.
 *
 * <p>The source type is declared as {@link ItemModel} to avoid a compile-time dependency on the
 * generated model class. At runtime the model is accessed via its getter methods through the
 * concrete subclass {@link DefaultExpressPaymentConfigConverter}.</p>
 */
public interface ExpressPaymentConfigConverter extends Converter<ItemModel, ExpressPaymentConfigDto> {

    @Override
    ExpressPaymentConfigDto convert(ItemModel source) throws ConversionException;

    @Override
    ExpressPaymentConfigDto convert(ItemModel source, ExpressPaymentConfigDto prototype) throws ConversionException;
}
