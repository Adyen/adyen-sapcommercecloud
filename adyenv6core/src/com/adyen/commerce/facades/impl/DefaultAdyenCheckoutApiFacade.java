package com.adyen.commerce.facades.impl;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.facades.AdyenPartialPaymentOrderFacade;
import com.adyen.commerce.services.AdyenStoredCardAuthorisationService;
import com.adyen.model.checkout.*;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.repository.AdyenPartialPaymentOrderRepository;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;
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
import java.util.ArrayList;
import java.util.List;

public class DefaultAdyenCheckoutApiFacade extends DefaultAdyenCheckoutFacade implements AdyenCheckoutApiFacade {

    public static final String EXCEPTION_DURING_PROCESSING_BROWSER_INFO = "Exception during processing BrowserInfo: ";

    private AdyenStoredCardAuthorisationService adyenStoredCardAuthorisationService;
    private AdyenPartialPaymentService adyenPartialPaymentService;
    private AdyenPartialPaymentOrderRepository adyenPartialPaymentOrderRepository;
    private AdyenPartialPaymentOrderFacade adyenPartialPaymentOrderFacade;

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
            } else if (CardDetails.TypeEnum.GIFTCARD.equals(cardDetails.getType())) {
                // Gift card
                paymentInfo.setAdyenGiftCardBrand(cardDetails.getBrand());
            }
        } else if (paymentRequest.getPaymentMethod().getActualInstance() instanceof PaymentDetails paymentDetails) {
            paymentInfo.setAdyenIssuerId(paymentDetails.getType().getValue());

        } else if (paymentRequest.getPaymentMethod().getActualInstance() instanceof AfterpayDetails) {
            paymentInfo.setAdyenTelephone(cartModel.getDeliveryAddress().getPhone1());

        } else if(paymentRequest.getPaymentMethod().getActualInstance() instanceof ApplePayDetails) {
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

        clearDonationSessionData();
        PaymentResponse paymentResponse;
        try {
            paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData, paymentRequest, requestInfo, getCheckoutCustomerStrategy().getCurrentUserForCheckout(), partialPaymentOrderData);
        } catch (PaymentAuthorisationRefusedException e) {
            // The remaining amount is refused after the gift card leg went through: release the held amount
            // instead of leaving the shopper's gift card reserved for a payment that will not happen.
            // Nothing reached Adyen, so the order to release is the one the Drop-in sent with the request.
            if (partialPaymentOrderData != null) {
                cancelPartialPaymentOrdersOnFailure(partialPaymentOrderData.getPspReference(), resolveOrderToCancel(null, paymentRequest));
            }
            throw e;
        }
        storeDonationPaymentData(paymentResponse);
        storeDonationCountryCode(cartData);
        if (PaymentResponse.ResultCodeEnum.PENDING == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.CHALLENGESHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.IDENTIFYSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER == paymentResponse.getResultCode()) {
            LOGGER.info("Placing pending order");
            OrderData orderData = placePendingOrder(paymentResponse.getResultCode().getValue(), paymentResponse.getAction());
            paymentResponse.setMerchantReference(orderData.getCode());
            throw new AdyenNonAuthorizedPaymentException(paymentResponse);
        }
        if (PaymentResponse.ResultCodeEnum.AUTHORISED == paymentResponse.getResultCode()) {
            LOGGER.info("Creating authorized order");
            OrderData authorizedOrder = createAuthorizedOrder(paymentResponse);
            return new OrderPaymentResult(authorizedOrder, paymentResponse);

        }

        // Payment failed — cancel any partial payment orders (gift card amounts) before throwing
        cancelPartialPaymentOrdersOnFailure(partialPaymentOrderData == null ? null : partialPaymentOrderData.getPspReference(),
                resolveOrderToCancel(paymentResponse, paymentRequest));
        throw new AdyenNonAuthorizedPaymentException(paymentResponse);
    }

    /**
     * Cancel all partial payment orders associated with the current session cart, without knowing the Adyen
     * order they belong to (falls back to the values stored on each record).
     */
    protected void cancelPartialPaymentOrdersOnFailure() {
        cancelPartialPaymentOrdersOnFailure(null, null, null);
    }

    protected void cancelPartialPaymentOrdersOnFailure(String orderPspReference, String orderData) {
        cancelPartialPaymentOrdersOnFailure(null, orderPspReference, orderData);
    }

    /**
     * @param currentPspReference balance-check psp of the partial payment of the current attempt, null if unknown
     * @param order               the Adyen order to cancel, null if unknown
     */
    protected void cancelPartialPaymentOrdersOnFailure(String currentPspReference, EncryptedOrderData order) {
        if (order == null) {
            cancelPartialPaymentOrdersOnFailure(currentPspReference, null, null);
        } else {
            cancelPartialPaymentOrdersOnFailure(currentPspReference, order.getPspReference(), order.getOrderData());
        }
    }

    /**
     * Picks the Adyen order to cancel: the one in the payment response (it carries the latest orderData),
     * otherwise the one the Drop-in sent with the payment request.
     *
     * @return the order, or null when neither carries an order PSP reference
     */
    protected EncryptedOrderData resolveOrderToCancel(PaymentResponse paymentResponse, PaymentRequest paymentRequest) {
        return resolveOrderToCancel(paymentResponse == null ? null : paymentResponse.getOrder(),
                paymentRequest == null ? null : paymentRequest.getOrder());
    }

    protected EncryptedOrderData resolveOrderToCancel(CheckoutOrderResponse responseOrder, EncryptedOrderData requestOrder) {
        if (responseOrder != null && StringUtils.isNotEmpty(responseOrder.getPspReference())) {
            String orderData = responseOrder.getOrderData();
            if (StringUtils.isEmpty(orderData) && requestOrder != null
                    && responseOrder.getPspReference().equals(requestOrder.getPspReference())) {
                orderData = requestOrder.getOrderData();
            }
            return new EncryptedOrderData().pspReference(responseOrder.getPspReference()).orderData(orderData);
        }
        if (requestOrder != null && StringUtils.isNotEmpty(requestOrder.getPspReference())) {
            return requestOrder;
        }
        return null;
    }

    /**
     * Cancel all partial payment orders associated with the current session cart.
     * This is called when a payment fails after a gift card has already been redeemed,
     * to release the held amount back to the gift card via Adyen /orders/cancel.
     * <p>
     * With an order pair /orders/cancel is called once, for the record of the current attempt (the newest
     * CREATED/AUTHORIZED one when it is not known - records are prepended to the cart). Other open records may
     * belong to an older Adyen order and are left alone. Without a pair each record is cancelled with its
     * own stored values, which Adyen will most likely reject.
     */
    protected void cancelPartialPaymentOrdersOnFailure(String currentPspReference, String orderPspReference, String orderData) {
        if (getAdyenPartialPaymentOrderFacade() == null) {
            LOGGER.warn("AdyenPartialPaymentOrderFacade not available — skipping partial payment order cancellation");
            return;
        }
        try {
            CartModel cartModel = getCartService().getSessionCart();
            if (cartModel == null || cartModel.getAdyenPartialPaymentOrders() == null) {
                return;
            }
            List<AdyenPartialPaymentOrderModel> toCancel = new ArrayList<>();
            for (AdyenPartialPaymentOrderModel partialPayment : cartModel.getAdyenPartialPaymentOrders()) {
                if (partialPayment.getPspReference() != null
                        && !partialPayment.getPspReference().isEmpty()
                        && (partialPayment.getStatus() == AdyenPartialPaymentStatus.CREATED
                            || partialPayment.getStatus() == AdyenPartialPaymentStatus.AUTHORIZED)) {
                    toCancel.add(partialPayment);
                }
            }
            if (toCancel.isEmpty()) {
                return;
            }
            if (StringUtils.isEmpty(orderPspReference)) {
                LOGGER.warn("No Adyen order PSP reference available — cancelling partial payment orders with the stored values, "
                        + "Adyen will likely reject the cancellation");
                for (AdyenPartialPaymentOrderModel partialPayment : toCancel) {
                    try {
                        getAdyenPartialPaymentOrderFacade().cancelPartialPaymentOrder(partialPayment.getPspReference());
                        LOGGER.info("Cancelled partial payment order on payment failure, PSP reference: " + partialPayment.getPspReference());
                    } catch (Exception e) {
                        LOGGER.error("Failed to cancel partial payment order with PSP reference: " + partialPayment.getPspReference(), e);
                    }
                }
                return;
            }
            AdyenPartialPaymentOrderModel current = toCancel.get(0);
            if (currentPspReference != null) {
                for (AdyenPartialPaymentOrderModel partialPayment : toCancel) {
                    if (currentPspReference.equals(partialPayment.getPspReference())) {
                        current = partialPayment;
                        break;
                    }
                }
            }
            try {
                getAdyenPartialPaymentOrderFacade().cancelPartialPaymentOrder(current.getPspReference(), orderPspReference, orderData);
            } catch (Exception e) {
                LOGGER.error("Failed to cancel partial payment order with order PSP reference: " + orderPspReference, e);
                return;
            }
            LOGGER.info("Cancelled partial payment order on payment failure, order PSP reference: " + orderPspReference);
            if (toCancel.size() > 1) {
                List<String> others = new ArrayList<>();
                for (AdyenPartialPaymentOrderModel partialPayment : toCancel) {
                    if (partialPayment != current) {
                        others.add(partialPayment.getPspReference());
                    }
                }
                LOGGER.warn("Partial payment orders not cancelled: may belong to another Adyen order, PSP references: " + others);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to cancel partial payment orders on payment failure", e);
        }
    }

    @Override
    public OrderPaymentResult placeOrderWithPaymentOCC(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo) throws Exception {
        requestInfo.setShopperLocale(getShopperLocale());

        clearDonationSessionData();
        PaymentResponse paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData, paymentRequest, requestInfo, getCheckoutCustomerStrategy().getCurrentUserForCheckout());
        storeDonationPaymentData(paymentResponse);
        storeDonationCountryCode(cartData);
        if (PaymentResponse.ResultCodeEnum.PENDING == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.CHALLENGESHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.IDENTIFYSHOPPER == paymentResponse.getResultCode()
                || PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER == paymentResponse.getResultCode()) {
            LOGGER.info("Placing pending order");
            OrderData orderData = placePendingOrder(paymentResponse.getResultCode().getValue(), paymentResponse.getAction());
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

        // Payment additional details failed or unexpected result — cancel any partial payment
        // orders (gift card amounts) before throwing
        // PaymentDetailsRequest carries no order, so without PaymentDetailsResponse.order there is no fallback
        // and the stored values are used.
        cancelPartialPaymentOrdersOnFailure(null, resolveOrderToCancel(paymentsDetailsResponse.getOrder(), null));
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
        if (Boolean.TRUE.equals(useAdyenDeliveryAddress)) {
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
    public void updatePartialPaymentAfterAuthorization(String pspReference, AdyenPartialPaymentStatus status, BigDecimal remainingAmount) {
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
            // Keyed on the balance-check pspReference: that is what the stored partial payment carries and
            // what the storefront sends back on the follow-up call for the remaining amount.
            updatePartialPaymentAfterAuthorization(
                    partialPaymentData.getPspReference(),
                    AdyenPartialPaymentStatus.AUTHORIZED,
                    remainingAmount
            );
            refreshPartialPaymentOrderData(partialPaymentData.getPspReference(), paymentResponse);
        }
        return paymentResponse;
    }

    /**
     * Adyen answers every payment made with an order by an updated order (new orderData and remainingAmount).
     * /orders/cancel only accepts the latest orderData, so the one stored when the order was created is stale
     * once the gift card has been charged.
     */
    protected void refreshPartialPaymentOrderData(String pspReference, PaymentResponse paymentResponse) {
        if (paymentResponse.getOrder() == null || paymentResponse.getOrder().getOrderData() == null
                || paymentResponse.getOrder().getOrderData().isEmpty()) {
            return;
        }
        try {
            AdyenPartialPaymentOrderModel partialPayment = adyenPartialPaymentOrderRepository.findPartialPaymentOrderByPspReference(pspReference);
            if (partialPayment != null) {
                partialPayment.setOrderData(paymentResponse.getOrder().getOrderData());
                getModelService().save(partialPayment);
            }
        } catch (Exception e) {
            // The gift card leg is already authorised: a failed refresh must not turn it into an error.
            LOGGER.warn("Failed to store the latest orderData for partial payment PSP reference: " + pspReference, e);
        }
    }

    @Override
    public PaymentResponse processZeroAuthCard(CheckoutPaymentMethod paymentMethod) throws Exception {
        final CustomerModel customer = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        final PaymentResponse response = getAdyenPaymentService().processZeroAuthRequest(customer, paymentMethod);
        // Adyen reports the network transaction id here and nowhere else - it is absent from the vaulted
        // token listing - so a card vaulted outside an order can only be made usable by a platform that
        // charges imported tokens as merchant-initiated if it is kept now.
        adyenStoredCardAuthorisationService.recordFrom(customer, merchantAccountOf(), response);
        return response;
    }

    protected String merchantAccountOf() {
        final AdyenCheckoutApiService service = getAdyenPaymentService();
        return service instanceof DefaultAdyenCheckoutApiService
                ? ((DefaultAdyenCheckoutApiService) service).getMerchantAccount()
                : null;
    }

    public AdyenPartialPaymentService getAdyenPartialPaymentService() {
        return adyenPartialPaymentService;
    }

    public void setAdyenStoredCardAuthorisationService(
            AdyenStoredCardAuthorisationService adyenStoredCardAuthorisationService) {
        this.adyenStoredCardAuthorisationService = adyenStoredCardAuthorisationService;
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

    public AdyenPartialPaymentOrderFacade getAdyenPartialPaymentOrderFacade() {
        return adyenPartialPaymentOrderFacade;
    }

    public void setAdyenPartialPaymentOrderFacade(AdyenPartialPaymentOrderFacade adyenPartialPaymentOrderFacade) {
        this.adyenPartialPaymentOrderFacade = adyenPartialPaymentOrderFacade;
    }
}
