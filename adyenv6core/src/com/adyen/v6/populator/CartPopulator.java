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

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.storesession.data.CurrencyData;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;

import java.util.ArrayList;
import java.util.List;

public class CartPopulator implements Populator<CartModel, CartData> {
    /**
     * {@inheritDoc}
     */
    @Override
    public void populate(final CartModel source, final CartData target) throws ConversionException {
        target.setAdyenDfValue(source.getAdyenDfValue());

        final PaymentInfoModel paymentInfo = source.getPaymentInfo();
        if (paymentInfo != null && isAdyenPaymentInfo(paymentInfo)) {
            target.setAdyenPaymentMethod(paymentInfo.getAdyenPaymentMethod());
            target.setAdyenIssuerId(paymentInfo.getAdyenIssuerId());
            target.setAdyenUPIVirtualAddress(paymentInfo.getAdyenUPIVirtualAddress());
            target.setAdyenRememberTheseDetails(paymentInfo.getAdyenRememberTheseDetails());
            target.setAdyenSelectedReference(paymentInfo.getAdyenSelectedReference());
            target.setAdyenDob(paymentInfo.getAdyenDob());
            target.setAdyenSocialSecurityNumber(paymentInfo.getAdyenSocialSecurityNumber());
            target.setAdyenFirstName(paymentInfo.getAdyenFirstName());
            target.setAdyenLastName(paymentInfo.getAdyenLastName());
            target.setAdyenCardHolder(paymentInfo.getAdyenCardHolder());
            target.setAdyenCardBrand(paymentInfo.getCardBrand());
            target.setAdyenCardType(paymentInfo.getCardType());
            target.setAdyenEncryptedCardNumber(paymentInfo.getEncryptedCardNumber());
            target.setAdyenEncryptedExpiryMonth(paymentInfo.getEncryptedExpiryMonth());
            target.setAdyenEncryptedExpiryYear(paymentInfo.getEncryptedExpiryYear());
            target.setAdyenEncryptedSecurityCode(paymentInfo.getEncryptedSecurityCode());
            target.setAdyenInstallments(paymentInfo.getAdyenInstallments());
            target.setAdyenTerminalId(paymentInfo.getAdyenTerminalId());
            target.setAdyenBrowserInfo(paymentInfo.getAdyenBrowserInfo());
            target.setAdyenSepaOwnerName(paymentInfo.getAdyenSepaOwnerName());
            target.setAdyenSepaIbanNumber(paymentInfo.getAdyenSepaIbanNumber());
            target.setAdyenApplePayMerchantName(paymentInfo.getAdyenApplePayMerchantName());
            target.setAdyenApplePayMerchantIdentifier(paymentInfo.getAdyenApplePayMerchantIdentifier());
            target.setAdyenShopperGender(paymentInfo.getAdyenShopperGender());
            target.setAdyenShopperEmail(paymentInfo.getAdyenShopperEmail());
            target.setAdyenShopperTelephone(paymentInfo.getAdyenTelephone());
            target.setAdyenGiftCardBrand(paymentInfo.getAdyenGiftCardBrand());
            target.setAdyenAmazonPayConfiguration(source.getAdyenAmazonPayConfiguration());
        }

        // Populate AdyenPartialPaymentOrders
        if (source.getAdyenPartialPaymentOrders() != null && !source.getAdyenPartialPaymentOrders().isEmpty()) {
            final List<AdyenPartialPaymentOrderData> partialPaymentOrderDataList = new ArrayList<>();
            for (final AdyenPartialPaymentOrderModel partialPaymentOrderModel : source.getAdyenPartialPaymentOrders()) {
                final AdyenPartialPaymentOrderData partialPaymentOrderData = convertToAdyenPartialPaymentOrderData(partialPaymentOrderModel);
                partialPaymentOrderDataList.add(partialPaymentOrderData);
            }
            target.setAdyenPartialPaymentOrders(partialPaymentOrderDataList);
        }
    }

    protected boolean isAdyenPaymentInfo(final PaymentInfoModel paymentInfo) {
        return ! (paymentInfo instanceof CreditCardPaymentInfoModel);
    }

    /**
     * Converts AdyenPartialPaymentOrderModel to AdyenPartialPaymentOrderData
     */
    protected AdyenPartialPaymentOrderData convertToAdyenPartialPaymentOrderData(final AdyenPartialPaymentOrderModel source) {
        final AdyenPartialPaymentOrderData target = new AdyenPartialPaymentOrderData();
        
        target.setPspReference(source.getPspReference());
        target.setRequestAmount(source.getRequestAmount());
        
        // Convert Currency to CurrencyData
        if (source.getCurrency() != null) {
            final CurrencyData currencyData = new CurrencyData();
            currencyData.setIsocode(source.getCurrency().getIsocode());
            currencyData.setName(source.getCurrency().getName());
            currencyData.setSymbol(source.getCurrency().getSymbol());
            target.setCurrency(currencyData);
        }
        
        target.setGiftCardBalance(source.getGiftCardBalance());
        target.setGiftCardChargedAmount(source.getGiftCardChargedAmount());
        target.setRemainingAmount(source.getRemainingAmount());
        target.setGiftCardNumber(source.getGiftCardNumber());
        target.setGiftCardBrand(source.getGiftCardBrand());
        target.setGiftCardType(source.getGiftCardType());
        
        // Convert enum status to string
        if (source.getStatus() != null) {
            target.setStatus(source.getStatus().getCode());
        }
        
        target.setPaymentMethod(source.getPaymentMethod());
        target.setCreatedAt(source.getCreatedAt());
        target.setProcessedAt(source.getProcessedAt());
        target.setBalanceCheckedAt(source.getBalanceCheckedAt());
        
        return target;
    }
}
