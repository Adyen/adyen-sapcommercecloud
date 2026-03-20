package com.adyen.commerce.facades;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.model.checkout.*;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import de.hybris.platform.commercefacades.order.data.CartData;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;

public interface AdyenCheckoutApiFacade extends AdyenCheckoutFacade {

    void preHandlePlaceOrder(PaymentRequest paymentRequest, String adyenPaymentMethod, AddressForm billingAddress, Boolean useAdyenDeliveryAddress);

    OrderPaymentResult placeOrderWithPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo) throws Exception;

    OrderPaymentResult placeOrderWithPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo, AdyenPartialPaymentOrderData partialPaymentOrderData) throws Exception;

    OrderPaymentResult placeOrderWithPaymentOCC(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo) throws Exception;

    OrderPaymentResult placeOrderWithAdditionalDetails(PaymentDetailsRequest detailsRequest) throws Exception;

    /**
     * Update partial payment order with authorization details
     *
     * @param pspReference The PSP reference of the partial payment
     * @param newPspReference The new PSP reference from authorization
     * @param status The new status
     * @param remainingAmount The remaining amount after partial payment
     */
    void updatePartialPaymentAfterAuthorization(String pspReference, String newPspReference, AdyenPartialPaymentStatus status, BigDecimal remainingAmount);

    /**
     * Update partial payment order status
     *
     * @param partialPaymentData The partial payment data
     * @param status The new status
     */
    void updatePartialPaymentStatus(AdyenPartialPaymentOrderData partialPaymentData, AdyenPartialPaymentStatus status);

    /**
     * Process partial payment authorization for gift cards
     * Makes authorization call to Adyen with the gift card amount instead of full cart amount
     *
     * @param cartData cart data
     * @param paymentRequest payment request object
     * @param requestInfo request information
     * @param customer customer model
     * @param partialPaymentData partial payment data containing gift card information
     * @return PaymentResponse from Adyen
     * @throws Exception if payment processing fails
     */
    PaymentResponse processPartialPaymentAuthorization(CartData cartData,
                                                       PaymentRequest paymentRequest,
                                                       com.adyen.v6.model.RequestInfo requestInfo,
                                                       de.hybris.platform.core.model.user.CustomerModel customer,
                                                       com.adyen.commerce.data.AdyenPartialPaymentOrderData partialPaymentData) throws Exception;

    PaymentResponse processZeroAuthCard(CheckoutPaymentMethod paymentMethod) throws Exception;
}
