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
package com.adyen.v6.forms;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.validation.constraints.NotNull;

import com.adyen.model.checkout.PaymentRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import com.adyen.util.Util;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONECLICK;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL;

/**
 * Form for select payment method page
 */
public class AdyenPaymentForm {
    private static final Logger LOG = Logger.getLogger(AdyenPaymentForm.class);

    private PaymentRequest paymentRequest;

    @NotNull
    private String paymentMethod;

    //CSE
    private String cseToken;

    //Secured Fields
    private String encryptedCardNumber;
    private String encryptedExpiryMonth;
    private String encryptedExpiryYear;
    private String encryptedSecurityCode;
    private String cardBrand;
    private String cardHolder;
    private String cardType;
    private String selectedReference;
    private int installments;

    private String riskData;

    //Save card
    private boolean rememberTheseDetails;

    //HPP
    private String issuerId;
    private String upiVirtualAddress;

    //SEPA direct debit fields
    private String sepaOwnerName;
    private String sepaIbanNumber;

    // openinvoice fields
    private String dob;
    private String dfValue;

    //Boleto
    private String firstName;
    private String lastName;

    // used in openinvoice and boleto
    private String socialSecurityNumber;

    //3DS 2.0
    private String browserInfo;

    //POS
    private String terminalId;

    // AfterPay fields
    private String gender;
    private String telephoneNumber;
    private String shopperEmail;

    // Gift Card
    private String giftCardBrand;

    //Billing address related fields
    private boolean useAdyenDeliveryAddress;
    private AddressForm billingAddress;

    public boolean getUseAdyenDeliveryAddress() {
        return useAdyenDeliveryAddress;
    }

    public void setUseAdyenDeliveryAddress(boolean useAdyenDeliveryAddress) {
        this.useAdyenDeliveryAddress = useAdyenDeliveryAddress;
    }

    public String getBrowserInfo() {
        return browserInfo;
    }

    public void setBrowserInfo(String browserInfo) {
        this.browserInfo = browserInfo;
    }

    public String getCseToken() {
        return cseToken;
    }

    public void setCseToken(String cseToken) {
        this.cseToken = cseToken;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getUpiVirtualAddress() {
        return upiVirtualAddress;
    }

    public void setUpiVirtualAddress(String upiVirtualAddress) {
        this.upiVirtualAddress = upiVirtualAddress;
    }

    public boolean getRememberTheseDetails() {
        return this.rememberTheseDetails;
    }

    public void setRememberTheseDetails(boolean rememberTheseDetails) {
        this.rememberTheseDetails = rememberTheseDetails;
    }

    public boolean isRememberTheseDetails() {
        return rememberTheseDetails;
    }

    public String getSelectedReference() {
        return selectedReference;
    }

    public void setSelectedReference(String selectedReference) {
        this.selectedReference = selectedReference;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public AddressForm getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(AddressForm billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getSepaOwnerName() {
        return sepaOwnerName;
    }

    public void setSepaOwnerName(String sepaOwnerName) {
        this.sepaOwnerName = sepaOwnerName;
    }

    public String getSepaIbanNumber() {
        return sepaIbanNumber;
    }

    public void setSepaIbanNumber(String sepaIbanNumber) {
        this.sepaIbanNumber = sepaIbanNumber;
    }

    public Date getDob() {
        Date dateOfBirth = null;
        if (dob != null) {
            try {
                // make sure the input format is yyyy-MM-dd
                if (dob.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    dateOfBirth = format.parse(dob);
                }
            } catch (Exception e) {
                LOG.error(e);
            }
        }
        return dateOfBirth;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getSocialSecurityNumber() {
        return socialSecurityNumber;
    }

    public void setSocialSecurityNumber(String socialSecurityNumber) {
        this.socialSecurityNumber = socialSecurityNumber;
    }

    public boolean isCC() {
        return PAYMENT_METHOD_CC.equals(paymentMethod);

    }

    public boolean isOneClick() {
        return PAYMENT_METHOD_ONECLICK.indexOf(paymentMethod) == 0;
    }

    public String getDfValue() {
        return dfValue;
    }

    public void setDfValue(String dfValue) {
        this.dfValue = dfValue;
    }

    public String getEncryptedCardNumber() {
        return encryptedCardNumber;
    }

    public void setEncryptedCardNumber(String encryptedCardNumber) {
        this.encryptedCardNumber = encryptedCardNumber;
    }

    public String getEncryptedExpiryMonth() {
        return encryptedExpiryMonth;
    }

    public void setEncryptedExpiryMonth(String encryptedExpiryMonth) {
        this.encryptedExpiryMonth = encryptedExpiryMonth;
    }

    public String getEncryptedExpiryYear() {
        return encryptedExpiryYear;
    }

    public void setEncryptedExpiryYear(String encryptedExpiryYear) {
        this.encryptedExpiryYear = encryptedExpiryYear;
    }

    public String getEncryptedSecurityCode() {
        return encryptedSecurityCode;
    }

    public void setEncryptedSecurityCode(String encryptedSecurityCode) {
        this.encryptedSecurityCode = encryptedSecurityCode;
    }

    public String getCardHolder() {
        return cardHolder;
    }

    public void setCardHolder(String cardHolder) {
        this.cardHolder = cardHolder;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public int getInstallments() {
        return installments;
    }

    public void setInstallments(int installments) {
        this.installments = installments;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public boolean usesComponent() {
        return PAYMENT_METHOD_PAYPAL.equals(paymentMethod);
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }

    public String getShopperEmail() {
        return shopperEmail;
    }

    public void setShopperEmail(String shopperEmail) {
        this.shopperEmail = shopperEmail;
    }

    public String getGiftCardBrand() {
        return giftCardBrand;
    }

    public void setGiftCardBrand(String giftCardBrand) {
        this.giftCardBrand = giftCardBrand;
    }

    public String getRiskData() {
        return riskData;
    }

    public void setRiskData(String riskData) {
        this.riskData = riskData;
    }

    public void resetFormExceptBillingAddress() {
        this.paymentMethod = null;
        this.cseToken = null;
        this.encryptedCardNumber = null;
        this.encryptedExpiryMonth = null;
        this.encryptedExpiryYear = null;
        this.encryptedSecurityCode = null;
        this.cardBrand = null;
        this.cardHolder = null;
        this.cardType = null;
        this.selectedReference = null;
        this.installments = 0;
        this.rememberTheseDetails = false;
        this.issuerId = null;
        this.dob = null;
        this.dfValue = null;
        this.firstName = null;
        this.lastName = null;
        this.socialSecurityNumber = null;
        this.browserInfo = null;
        this.terminalId = null;
        this.useAdyenDeliveryAddress = false;
        this.sepaIbanNumber = null;
        this.sepaOwnerName = null;
        this.billingAddress = null;
        this.shopperEmail = null;
        this.telephoneNumber = null;
        this.gender = null;
        this.giftCardBrand = null;
        this.riskData = null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class AdyenPaymentForm {\n");
        sb.append("    paymentMethod: ").append(Util.toIndentedString(paymentMethod)).append("\n");
        sb.append("    cseToken isEmpty?: ").append(StringUtils.isEmpty(cseToken)).append("\n");
        sb.append("    encryptedCardNumber isEmpty?: ").append(StringUtils.isEmpty(encryptedCardNumber)).append("\n");
        sb.append("    encryptedExpiryMonth isEmpty?: ").append(StringUtils.isEmpty(encryptedExpiryMonth)).append("\n");
        sb.append("    encryptedExpiryYear isEmpty?: ").append(StringUtils.isEmpty(encryptedExpiryYear)).append("\n");
        sb.append("    encryptedSecurityCode isEmpty?: ").append(StringUtils.isEmpty(encryptedSecurityCode)).append("\n");
        sb.append("    cardHolder: ").append(Util.toIndentedString(cardHolder)).append("\n");
        sb.append("    cardType: ").append(Util.toIndentedString(cardType)).append("\n");
        sb.append("    installments: ").append(Util.toIndentedString(installments)).append("\n");
        sb.append("    issuerId: ").append(Util.toIndentedString(issuerId)).append("\n");
        sb.append("    sepaOwnerName: ").append(Util.toIndentedString(sepaOwnerName)).append("\n");
        sb.append("    sepaIbanNumber: ").append(Util.toIndentedString(sepaIbanNumber)).append("\n");
        sb.append("    rememberTheseDetails: ").append(Util.toIndentedString(rememberTheseDetails)).append("\n");
        sb.append("    selectedReference: ").append(Util.toIndentedString(selectedReference)).append("\n");
        sb.append("    dateOfBirth: ").append(Util.toIndentedString(dob)).append("\n");
        sb.append("    shopperEmail: ").append(Util.toIndentedString(shopperEmail)).append("\n");
        sb.append("    telephoneNumber: ").append(Util.toIndentedString(telephoneNumber)).append("\n");
        sb.append("    gender: ").append(Util.toIndentedString(gender)).append("\n");
        sb.append("    socialSecurityNumber: ").append(Util.toIndentedString(socialSecurityNumber)).append("\n");
        sb.append("    firstName: ").append(Util.toIndentedString(firstName)).append("\n");
        sb.append("    lastName: ").append(Util.toIndentedString(lastName)).append("\n");
        sb.append("    dfValue: ").append(Util.toIndentedString(dfValue)).append("\n");
        sb.append("    cardBrand: ").append(Util.toIndentedString(cardBrand)).append("\n");
        sb.append("    terminalId: ").append(Util.toIndentedString(terminalId)).append("\n");
        sb.append("    browserInfo: ").append(Util.toIndentedString(browserInfo)).append("\n");
        sb.append("    useAdyenDeliveryAddress: ").append(Util.toIndentedString(useAdyenDeliveryAddress)).append("\n");
        sb.append("    billingAddress: ").append(Util.toIndentedString(billingAddress)).append("\n");
        sb.append("    giftCardBrand: ").append(Util.toIndentedString(giftCardBrand)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    public PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    public void setPaymentRequest(PaymentRequest paymentRequest) {
        this.paymentRequest = paymentRequest;
    }
}
