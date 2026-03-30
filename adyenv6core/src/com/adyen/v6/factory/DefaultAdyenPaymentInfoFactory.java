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
package com.adyen.v6.factory;

import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.forms.AdyenPaymentForm;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

/**
 * Default implementation of {@link AdyenPaymentInfoFactory}.
 * Creates and persists {@link PaymentInfoModel} instances from Accelerator form data
 * or OCC payment detail DTOs.
 */
public class DefaultAdyenPaymentInfoFactory implements AdyenPaymentInfoFactory {

    private ModelService modelService;
    private I18NFacade i18NFacade;
    private Converter<AddressData, AddressModel> addressReverseConverter;

    @Override
    public PaymentInfoModel createFromPaymentForm(final CartModel cartModel, final AdyenPaymentForm adyenPaymentForm) {
        final PaymentInfoModel paymentInfo = modelService.create(PaymentInfoModel.class);
        paymentInfo.setUser(cartModel.getUser());
        paymentInfo.setSaved(false);
        paymentInfo.setCode(generateCcPaymentInfoCode(cartModel));

        if (Boolean.TRUE.equals(adyenPaymentForm.getUseAdyenDeliveryAddress())) {
            // Clone DeliveryAddress to BillingAddress
            final AddressModel clonedAddress = modelService.clone(cartModel.getDeliveryAddress());
            clonedAddress.setBillingAddress(true);
            clonedAddress.setOwner(paymentInfo);
            paymentInfo.setBillingAddress(clonedAddress);
        } else {
            final AddressModel billingAddress = convertToAddressModel(adyenPaymentForm.getBillingAddress());
            billingAddress.setOwner(paymentInfo);
            paymentInfo.setBillingAddress(billingAddress);
        }

        paymentInfo.setAdyenPaymentMethod(adyenPaymentForm.getPaymentMethod());
        paymentInfo.setAdyenIssuerId(adyenPaymentForm.getIssuerId());
        paymentInfo.setAdyenUPIVirtualAddress(adyenPaymentForm.getUpiVirtualAddress());
        paymentInfo.setAdyenRememberTheseDetails(adyenPaymentForm.getRememberTheseDetails());
        paymentInfo.setAdyenSelectedReference(adyenPaymentForm.getSelectedReference());

        // openinvoice fields
        paymentInfo.setAdyenDob(adyenPaymentForm.getDob());
        paymentInfo.setAdyenSocialSecurityNumber(adyenPaymentForm.getSocialSecurityNumber());
        paymentInfo.setAdyenSepaOwnerName(adyenPaymentForm.getSepaOwnerName());
        paymentInfo.setAdyenSepaIbanNumber(adyenPaymentForm.getSepaIbanNumber());

        // AfterPay fields
        paymentInfo.setAdyenTelephone(cartModel.getDeliveryAddress().getPhone1());
        paymentInfo.setAdyenShopperEmail(adyenPaymentForm.getShopperEmail());
        paymentInfo.setAdyenShopperGender(adyenPaymentForm.getGender());

        // Boleto fields
        paymentInfo.setAdyenFirstName(adyenPaymentForm.getFirstName());
        paymentInfo.setAdyenLastName(adyenPaymentForm.getLastName());

        paymentInfo.setAdyenCardHolder(adyenPaymentForm.getCardHolder());

        // required for 3DS2
        paymentInfo.setAdyenBrowserInfo(adyenPaymentForm.getBrowserInfo());

        // pos field(s)
        paymentInfo.setAdyenTerminalId(adyenPaymentForm.getTerminalId());

        // apple pay
        paymentInfo.setAdyenApplePayMerchantName(cartModel.getAdyenApplePayMerchantName());
        paymentInfo.setAdyenApplePayMerchantIdentifier(cartModel.getAdyenApplePayMerchantIdentifier());

        // combo card fields
        paymentInfo.setCardType(adyenPaymentForm.getCardType());
        paymentInfo.setCardBrand(adyenPaymentForm.getCardBrand());

        // Gift card
        paymentInfo.setAdyenGiftCardBrand(adyenPaymentForm.getGiftCardBrand());

        modelService.save(paymentInfo);
        return paymentInfo;
    }

    @Override
    public PaymentInfoModel createFromPaymentDetails(final CartModel cartModel, final PaymentDetailsWsDTO paymentDetails) {
        final PaymentInfoModel paymentInfo = modelService.create(PaymentInfoModel.class);
        paymentInfo.setUser(cartModel.getUser());
        paymentInfo.setSaved(false);
        paymentInfo.setCode(generateCcPaymentInfoCode(cartModel));

        paymentInfo.setAdyenIssuerId(paymentDetails.getIssueNumber());
        paymentInfo.setAdyenCardHolder(paymentDetails.getAccountHolderName());
        paymentInfo.setEncryptedCardNumber(paymentDetails.getEncryptedCardNumber());
        paymentInfo.setEncryptedExpiryMonth(paymentDetails.getEncryptedExpiryMonth());
        paymentInfo.setEncryptedExpiryYear(paymentDetails.getEncryptedExpiryYear());
        paymentInfo.setEncryptedSecurityCode(paymentDetails.getEncryptedSecurityCode());
        paymentInfo.setAdyenRememberTheseDetails(paymentDetails.getSaveCardData());
        paymentInfo.setAdyenPaymentMethod(paymentDetails.getAdyenPaymentMethod());
        paymentInfo.setAdyenSelectedReference(paymentDetails.getAdyenSelectedReference());
        paymentInfo.setAdyenSocialSecurityNumber(paymentDetails.getAdyenSocialSecurityNumber());
        paymentInfo.setAdyenSepaOwnerName(paymentDetails.getAdyenSepaOwnerName());
        paymentInfo.setAdyenSepaIbanNumber(paymentDetails.getAdyenSepaIbanNumber());
        paymentInfo.setAdyenFirstName(paymentDetails.getAdyenFirstName());
        paymentInfo.setAdyenLastName(paymentDetails.getAdyenLastName());
        paymentInfo.setOwner(cartModel.getOwner());
        paymentInfo.setAdyenTerminalId(paymentDetails.getTerminalId());
        paymentInfo.setAdyenInstallments(paymentDetails.getInstallments());

        modelService.save(paymentInfo);
        return paymentInfo;
    }

    protected String generateCcPaymentInfoCode(final CartModel cartModel) {
        return cartModel.getCode() + "_" + UUID.randomUUID();
    }

    protected AddressModel convertToAddressModel(final AddressForm addressForm) {
        final AddressData addressData = convertToAddressData(addressForm);
        final AddressModel billingAddress = modelService.create(AddressModel.class);
        addressReverseConverter.convert(addressData, billingAddress);
        return billingAddress;
    }

    protected AddressData convertToAddressData(final AddressForm addressForm) {
        final AddressData addressData = new AddressData();
        final CountryData countryData = i18NFacade.getCountryForIsocode(addressForm.getCountryIso());
        addressData.setTitleCode(addressForm.getTitleCode());
        addressData.setFirstName(addressForm.getFirstName());
        addressData.setLastName(addressForm.getLastName());
        addressData.setLine1(addressForm.getLine1());
        addressData.setLine2(addressForm.getLine2());
        addressData.setTown(addressForm.getTownCity());
        addressData.setPostalCode(addressForm.getPostcode());
        addressData.setBillingAddress(true);
        addressData.setCountry(countryData);
        addressData.setPhone(addressForm.getPhoneNumber());
        addressData.setCompanyName(addressForm.getCompanyName());
        addressData.setTaxNumber(addressForm.getTaxNumber());
        addressData.setRegistrationNumber(addressForm.getRegistrationNumber());

        if (addressForm.getRegionIso() != null && !StringUtils.isEmpty(addressForm.getRegionIso())) {
            final RegionData regionData = i18NFacade.getRegion(addressForm.getCountryIso(), addressForm.getRegionIso());
            addressData.setRegion(regionData);
        }
        return addressData;
    }

    // --- Getters / Setters ---

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(final ModelService modelService) {
        this.modelService = modelService;
    }

    public I18NFacade getI18NFacade() {
        return i18NFacade;
    }

    public void setI18NFacade(final I18NFacade i18NFacade) {
        this.i18NFacade = i18NFacade;
    }

    public Converter<AddressData, AddressModel> getAddressReverseConverter() {
        return addressReverseConverter;
    }

    public void setAddressReverseConverter(final Converter<AddressData, AddressModel> addressReverseConverter) {
        this.addressReverseConverter = addressReverseConverter;
    }
}
