package com.adyen.commerce.facades.impl;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.model.checkout.*;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.repository.AdyenPartialPaymentOrderRepository;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

public class DefaultAdyenCheckoutApiFacade extends DefaultAdyenCheckoutFacade implements AdyenCheckoutApiFacade {

    public static final String EXCEPTION_DURING_PROCESSING_BROWSER_INFO = "Exception during processing BrowserInfo: ";

    private AdyenPartialPaymentService adyenPartialPaymentService;
    private AdyenPartialPaymentOrderRepository adyenPartialPaymentOrderRepository;

    public void preHandlePlaceOrder(PaymentRequest paymentRequest, String adyenPaymentMethod,
                                    AddressForm billingAddress, Boolean useAdyenDeliveryAddress) {

        CartModel cartModel = getCartService().getSessionCart();

        PaymentInfoModel paymentInfo = createPaymentInfo(cartModel, adyenPaymentMethod);

        saveBillingAddress(billingAddress, paymentInfo, cartModel, useAdyenDeliveryAddress);

        //required for 3DS2
        saveBrowserInfoOnPaymentInfo(paymentRequest.getBrowserInfo(), paymentInfo);

        if (paymentRequest.getPaymentMethod().getActualInstance() instanceof CardDetails cardDetails) {
            paymentInfo.setCardType(cardDetails.getType().getValue());
            if (CardDetails.TypeEnum.CARD.equals(cardDetails.getType()) ||
                    CardDetails.TypeEnum.SCHEME.equals(cardDetails.getType()) ||
                    CardDetails.TypeEnum.BCMC.equals(cardDetails.getType())) {
                paymentInfo.setAdyenCardHolder(cardDetails.getHolderName());
                paymentInfo.setCardBrand(cardDetails.getBrand());
                paymentInfo.setAdyenSelectedReference(cardDetails.getStoredPaymentMethodId());
                paymentInfo.setAdyenRememberTheseDetails(paymentRequest.getStorePaymentMethod());
                paymentInfo.setAdyenSelectedReference(cardDetails.getStoredPaymentMethodId());
                paymentInfo.setAdyenRememberTheseDetails(paymentRequest.getEnableOneClick());
                paymentInfo.setAdyenSelectedReference(cardDetails.getStoredPaymentMethodId());
            } else if (CardDetails.TypeEnum.GIFTCARD.equals(cardDetails.getType())) {
                // Gift card
                paymentInfo.setAdyenGiftCardBrand(cardDetails.getBrand());
            }
        } else if (paymentRequest.getPaymentMethod().getActualInstance() instanceof PaymentDetails paymentDetails) {
            paymentInfo.setAdyenIssuerId(paymentDetails.getType().getValue());

        } else if (paymentRequest.getPaymentMethod().getActualInstance() instanceof AfterpayDetails afterpayDetails) {
            paymentInfo.setAdyenTelephone(cartModel.getDeliveryAddress().getPhone1());

        } else if(paymentRequest.getPaymentMethod().getActualInstance() instanceof ApplePayDetails applePayDetails){
            paymentInfo.setAdyenApplePayMerchantName(cartModel.getAdyenApplePayMerchantName());
            paymentInfo.setAdyenApplePayMerchantIdentifier(cartModel.getAdyenApplePayMerchantIdentifier());
        }


        getTransactionTemplate().execute(transactionStatus -> {
            //Create payment info
            getModelService().save(paymentInfo);
            cartModel.setPaymentInfo(paymentInfo);
            getModelService().save(cartModel);
            return null;
        });
    }

    protected static void saveBrowserInfoOnPaymentInfo(BrowserInfo browserInfo, PaymentInfoModel paymentInfo) {
        if (browserInfo != null) {
            paymentInfo.setAdyenBrowserInfo(getBrowserInfoJson(browserInfo));
        }
    }

    protected static String getBrowserInfoJson(BrowserInfo browserInfo) {
        try {
            return browserInfo.toJson();
        } catch (JsonProcessingException e) {
            LOGGER.error(EXCEPTION_DURING_PROCESSING_BROWSER_INFO, e);
            return StringUtils.EMPTY;
        }
    }

    @Override
    public OrderPaymentResult placeOrderWithPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo) throws Exception {
        return placeOrderWithPayment(request, cartData, paymentRequest, requestInfo, null);
    }

    @Override
    public OrderPaymentResult placeOrderWithPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo, AdyenPartialPaymentOrderData partialPaymentOrderData) throws Exception{
        requestInfo.setShopperLocale(getShopperLocale());

        PaymentResponse paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData, paymentRequest, requestInfo, getCheckoutCustomerStrategy().getCurrentUserForCheckout(), partialPaymentOrderData);
        if (PaymentResponse.ResultCodeEnum.PENDING == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.CHALLENGESHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.IDENTIFYSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER == paymentResponse.getResultCode()) {
            LOGGER.info("Placing pending order");
            OrderData orderData = placePendingOrder(paymentResponse.getResultCode().getValue());
            paymentResponse.setMerchantReference(orderData.getCode());
            throw new AdyenNonAuthorizedPaymentException(paymentResponse);
        }
        if (PaymentResponse.ResultCodeEnum.AUTHORISED == paymentResponse.getResultCode()) {
            LOGGER.info("Creating authorized order");
            OrderData authorizedOrder = createAuthorizedOrder(paymentResponse);
            return new OrderPaymentResult(authorizedOrder, paymentResponse);

        }

        throw new AdyenNonAuthorizedPaymentException(paymentResponse);
    }

    @Override
    public OrderPaymentResult placeOrderWithPaymentOCC(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo) throws Exception {
        requestInfo.setShopperLocale(getShopperLocale());

        PaymentResponse paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData, paymentRequest, requestInfo, getCheckoutCustomerStrategy().getCurrentUserForCheckout());
        if (PaymentResponse.ResultCodeEnum.PENDING == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.CHALLENGESHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.IDENTIFYSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER == paymentResponse.getResultCode()) {
            LOGGER.info("Placing pending order");
            OrderData orderData = placePendingOrder(paymentResponse.getResultCode().getValue());
            paymentResponse.setMerchantReference(orderData.getCode());
            return new OrderPaymentResult(orderData, paymentResponse);
        }
        if (PaymentResponse.ResultCodeEnum.AUTHORISED == paymentResponse.getResultCode()) {
            LOGGER.info("Creating authorized order");
            OrderData authorizedOrder = createAuthorizedOrder(paymentResponse);
            return new OrderPaymentResult(authorizedOrder, paymentResponse);

        }

        return new OrderPaymentResult(null, paymentResponse);
    }

    @Override
    public OrderPaymentResult placeOrderWithAdditionalDetails(PaymentDetailsRequest detailsRequest) throws Exception {

        PaymentDetailsResponse paymentsDetailsResponse = this.componentDetails(detailsRequest);

        if (PaymentDetailsResponse.ResultCodeEnum.PENDING == paymentsDetailsResponse.getResultCode()
                || PaymentDetailsResponse.ResultCodeEnum.REDIRECTSHOPPER == paymentsDetailsResponse.getResultCode()
                || PaymentDetailsResponse.ResultCodeEnum.CHALLENGESHOPPER == paymentsDetailsResponse.getResultCode()
                || PaymentDetailsResponse.ResultCodeEnum.IDENTIFYSHOPPER == paymentsDetailsResponse.getResultCode()
                || PaymentDetailsResponse.ResultCodeEnum.PRESENTTOSHOPPER == paymentsDetailsResponse.getResultCode()) {
            LOGGER.info("Placing pending order");
            placePendingOrder(paymentsDetailsResponse.getResultCode().getValue());
            throw new AdyenNonAuthorizedPaymentException(paymentsDetailsResponse);
        }
        if (PaymentDetailsResponse.ResultCodeEnum.AUTHORISED == paymentsDetailsResponse.getResultCode() ||
                PaymentDetailsResponse.ResultCodeEnum.RECEIVED == paymentsDetailsResponse.getResultCode()) {
            LOGGER.info("Creating authorized order");
            String orderCode = paymentsDetailsResponse.getMerchantReference();
            OrderModel orderModel = retrievePendingOrder(orderCode);
            return new OrderPaymentResult(getOrderConverter().convert(orderModel), paymentsDetailsResponse);
        }

        throw new AdyenNonAuthorizedPaymentException(paymentsDetailsResponse);
    }


    public void saveBillingAddress(AddressForm billingAddress, PaymentInfoModel paymentInfo, CartModel cartModel, Boolean useAdyenDeliveryAddress) {
        if (!getCheckoutCustomerStrategy().isAnonymousCheckout() && billingAddress != null
                && billingAddress.isSaveInAddressBook()) {
            AddressData addressData = convertToAddressData(billingAddress);
            addressData.setVisibleInAddressBook(true);
            addressData.setShippingAddress(true);
            getUserFacade().addAddress(addressData);
        }
        if (useAdyenDeliveryAddress == true) {
            // Clone DeliveryAdress to BillingAddress
            final AddressModel clonedAddress = getModelService().clone(cartModel.getDeliveryAddress());
            clonedAddress.setBillingAddress(true);
            clonedAddress.setOwner(paymentInfo);
            paymentInfo.setBillingAddress(clonedAddress);
        } else {
            AddressModel billingAddressModel = convertToAddressModel(billingAddress);
            billingAddressModel.setOwner(paymentInfo);
            paymentInfo.setBillingAddress(billingAddressModel);
        }
    }

    public PaymentInfoModel createPaymentInfo(final CartModel cartModel, String adyenPaymentMethod) {
        final PaymentInfoModel paymentInfo = getModelService().create(PaymentInfoModel.class);
        paymentInfo.setUser(cartModel.getUser());
        paymentInfo.setSaved(false);
        paymentInfo.setCode(generateCcPaymentInfoCode(cartModel));
        paymentInfo.setAdyenPaymentMethod(adyenPaymentMethod);

        getModelService().save(paymentInfo);

        return paymentInfo;
    }

    @Override
    public void updatePartialPaymentAfterAuthorization(String pspReference, String newPspReference, AdyenPartialPaymentStatus status, BigDecimal remainingAmount) {
        AdyenPartialPaymentOrderModel partialPayment = adyenPartialPaymentOrderRepository.findPartialPaymentOrderByPspReference(pspReference);
        if (partialPayment != null) {
            partialPayment.setStatus(status);
            partialPayment.setProcessedAt(new java.util.Date());
            partialPayment.setRemainingAmount(remainingAmount);
            getModelService().save(partialPayment);
        }
    }

    @Override
    public void updatePartialPaymentStatus(AdyenPartialPaymentOrderData partialPaymentData, AdyenPartialPaymentStatus status) {
        AdyenPartialPaymentOrderModel partialPayment = adyenPartialPaymentOrderRepository.findPartialPaymentOrderByPspReference(partialPaymentData.getPspReference());
        if (partialPayment != null) {
            partialPayment.setStatus(status);
            partialPayment.setProcessedAt(new java.util.Date());
            getModelService().save(partialPayment);
        }
    }

    /**
     * Process partial payment authorization for gift cards
     * Makes authorization call to Adyen with the gift card amount instead of full cart amount
     */
    public PaymentResponse processPartialPaymentAuthorization(CartData cartData,
                                                              PaymentRequest paymentRequest,
                                                              RequestInfo requestInfo, CustomerModel customer,
                                                              AdyenPartialPaymentOrderData partialPaymentData) throws Exception {
        // Get Adyen checkout API service
        AdyenCheckoutApiService adyenService = getAdyenPaymentService();

        // Make authorization call to Adyen with the gift card amount instead of full cart amount
        PaymentResponse paymentResponse = adyenService.processPartialPaymentRequest(
                cartData,
                paymentRequest,
                requestInfo,
                customer,
                partialPaymentData.getGiftCardChargedAmount(),
                partialPaymentData.getCurrency().getIsocode()
        );

        LOGGER.info("Gift card authorization response: " + paymentResponse.getResultCode() +
                " PSP Reference: " + paymentResponse.getPspReference());

        // Handle the payment response
        if (PaymentResponse.ResultCodeEnum.AUTHORISED == paymentResponse.getResultCode()) {
            // Calculate remaining amount (total cart amount - gift card charged amount)
            java.math.BigDecimal totalAmount = cartData.getTotalPrice().getValue();
            java.math.BigDecimal giftCardAmount = partialPaymentData.getGiftCardChargedAmount();
            java.math.BigDecimal remainingAmount = totalAmount.subtract(giftCardAmount);

            // Update the partial payment through facade
            updatePartialPaymentAfterAuthorization(
                    partialPaymentData.getPspReference(),
                    paymentResponse.getPspReference(),
                    AdyenPartialPaymentStatus.AUTHORIZED,
                    remainingAmount
            );
        }
        return paymentResponse;
    }

    @Override
    public PaymentResponse processZeroAuthCard(CheckoutPaymentMethod paymentMethod) throws Exception {
        final CustomerModel customer = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        return getAdyenPaymentService().processZeroAuthRequest(customer, paymentMethod);
    }

    public AdyenPartialPaymentService getAdyenPartialPaymentService() {
        return adyenPartialPaymentService;
    }

    public void setAdyenPartialPaymentService(AdyenPartialPaymentService adyenPartialPaymentService) {
        this.adyenPartialPaymentService = adyenPartialPaymentService;
    }

    public AdyenPartialPaymentOrderRepository getAdyenPartialPaymentOrderRepository() {
        return adyenPartialPaymentOrderRepository;
    }

    public void setAdyenPartialPaymentOrderRepository(AdyenPartialPaymentOrderRepository adyenPartialPaymentOrderRepository) {
        this.adyenPartialPaymentOrderRepository = adyenPartialPaymentOrderRepository;
    }
}
