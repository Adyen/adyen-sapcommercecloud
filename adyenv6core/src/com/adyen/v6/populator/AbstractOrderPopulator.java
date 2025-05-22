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

import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import org.springframework.beans.factory.annotation.Required;

public class AbstractOrderPopulator implements Populator<AbstractOrderModel, AbstractOrderData> {
    private Converter<AddressModel, AddressData> addressConverter;

    /**
     * {@inheritDoc}
     */
    @Override
    public void populate(final AbstractOrderModel source, final AbstractOrderData target) throws ConversionException {
        final PaymentInfoModel paymentInfo = source.getPaymentInfo();
        if (paymentInfo != null && isAdyenPaymentInfo(paymentInfo)) {
            // In the OrderConfirmationPage, orderData.PaymentInfo is "required" to be a CCPaymentInfoData, and cannot be null.
            final CCPaymentInfoData ccPaymentInfoData = new CCPaymentInfoData();
            ccPaymentInfoData.setBillingAddress(addressConverter.convert(paymentInfo.getBillingAddress()));
            target.setPaymentInfo(ccPaymentInfoData);

            //Set boleto url
            target.setAdyenBoletoUrl(paymentInfo.getAdyenBoletoUrl());
            target.setAdyenBoletoBarCodeReference(paymentInfo.getAdyenBoletoBarCodeReference());
            target.setAdyenBoletoDueDate(paymentInfo.getAdyenBoletoDueDate());
            target.setAdyenBoletoExpirationDate(paymentInfo.getAdyenBoletoExpirationDate());
            //Set multibanco
            target.setAdyenMultibancoAmount(paymentInfo.getAdyenMultibancoAmount());
            target.setAdyenMultibancoDeadline(paymentInfo.getAdyenMultibancoDeadline());
            target.setAdyenMultibancoReference(paymentInfo.getAdyenMultibancoReference());
            target.setAdyenMultibancoEntity(paymentInfo.getAdyenMultibancoEntity());
        }
    }

    protected boolean isAdyenPaymentInfo(final PaymentInfoModel paymentInfo) {
        return !(paymentInfo instanceof CreditCardPaymentInfoModel);
    }

    @Required
    public void setAddressConverter(Converter<AddressModel, AddressData> addressConverter) {
        this.addressConverter = addressConverter;
    }
}
