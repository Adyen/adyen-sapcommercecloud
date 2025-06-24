package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.*;
import com.adyen.model.recurring.Recurring;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;
import org.apache.commons.lang3.StringUtils;

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

/**
 * Handler for credit card payments
 */
public class CreditCardPaymentHandler implements PaymentMethodHandler {

    @Override
    public boolean canHandle(String paymentMethod) {
        return PAYMENT_METHOD_CC.equals(paymentMethod) || PAYMENT_METHOD_BCMC.equals(paymentMethod);
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData,
                                   RecurringContractMode recurringContractMode,
                                   CustomerModel customerModel, Boolean is3DS2Allowed,
                                   Boolean guestUserTokenizationEnabled) {
        
        if (CARD_TYPE_DEBIT.equals(cartData.getAdyenCardType())) {
            updateForDebitCard(paymentRequest, cartData, recurringContractMode);
        } else {
            updateForCreditCard(paymentRequest, cartData, recurringContractMode);
        }

        if (Boolean.TRUE.equals(is3DS2Allowed)) {
            ThreeDSEnhancer.enhance3DS2(paymentRequest, cartData);
        }

        if (customerModel.getType() == CustomerType.GUEST && Boolean.TRUE.equals(guestUserTokenizationEnabled)) {
            paymentRequest.setEnableOneClick(false);
        }

        setInstallments(paymentRequest, cartData);
    }

    private void updateForCreditCard(PaymentRequest paymentRequest, CartData cartData,
                                   RecurringContractMode recurringContractMode) {
        Recurring recurringContract = RecurringContractHelper.getRecurringContractType(recurringContractMode);
        
        if (recurringContract != null) {
            handleRecurringContract(paymentRequest, cartData, recurringContract.getContract());
        }

        setCardDetails(paymentRequest, cartData, false);
    }

    private void updateForDebitCard(PaymentRequest paymentRequest, CartData cartData,
                                  RecurringContractMode recurringContractMode) {
        Recurring recurringContract = RecurringContractHelper.getRecurringContractType(recurringContractMode);
        
        if (recurringContract != null && 
            (Recurring.ContractEnum.RECURRING.equals(recurringContract.getContract()) || 
             Recurring.ContractEnum.ONECLICK.equals(recurringContract.getContract())) &&
            Boolean.TRUE.equals(cartData.getAdyenRememberTheseDetails())) {
            paymentRequest.setEnableOneClick(true);
        }

        setCardDetails(paymentRequest, cartData, true);
        paymentRequest.putAdditionalDataItem("overwriteBrand", "true");
    }

    private void handleRecurringContract(PaymentRequest paymentRequest, CartData cartData,
                                       Recurring.ContractEnum contract) {
        if (Recurring.ContractEnum.RECURRING.equals(contract)) {
            paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);
            paymentRequest.setEnableRecurring(true);
            if (Boolean.TRUE.equals(cartData.getAdyenRememberTheseDetails())) {
                paymentRequest.setEnableOneClick(true);
            }
        } else if (Recurring.ContractEnum.ONECLICK.equals(contract) && 
                   Boolean.TRUE.equals(cartData.getAdyenRememberTheseDetails())) {
            paymentRequest.setEnableOneClick(true);
        }
    }

    private void setCardDetails(PaymentRequest paymentRequest, CartData cartData, boolean isDebitCard) {
        String encryptedCardNumber = cartData.getAdyenEncryptedCardNumber();
        String encryptedExpiryMonth = cartData.getAdyenEncryptedExpiryMonth();
        String encryptedExpiryYear = cartData.getAdyenEncryptedExpiryYear();

        if (StringUtils.isNotEmpty(encryptedCardNumber) && 
            StringUtils.isNotEmpty(encryptedExpiryMonth) && 
            StringUtils.isNotEmpty(encryptedExpiryYear)) {
            
            CardDetails cardDetails = new CardDetails()
                .encryptedCardNumber(encryptedCardNumber)
                .encryptedExpiryMonth(encryptedExpiryMonth)
                .encryptedExpiryYear(encryptedExpiryYear)
                .encryptedSecurityCode(cartData.getAdyenEncryptedSecurityCode())
                .holderName(cartData.getAdyenCardHolder());

            if (!isDebitCard) {
                cardDetails.type(CardDetails.TypeEnum.CARD);
            } else {
                cardDetails.brand(cartData.getAdyenCardBrand());
            }

            paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(cardDetails));
        }
    }


    private void setInstallments(PaymentRequest paymentRequest, CartData cartData) {
        if (cartData.getAdyenInstallments() != null) {
            Installments installmentObj = new Installments();
            installmentObj.setValue(cartData.getAdyenInstallments());
            paymentRequest.setInstallments(installmentObj);
        }
    }
}