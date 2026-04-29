package com.adyen.commerce.facades;

import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeValueModel;
import de.hybris.platform.order.exceptions.CalculationException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;

/**
 * Facade responsible for orchestrating express checkout flows (PDP and Cart, both Accelerator and OCC variants).
 */
public interface AdyenExpressCheckoutFacade {

    PaymentResponse expressCheckoutPDP(String cartId, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                       HttpServletRequest request) throws Exception;

    PaymentResponse expressCheckoutCart(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                        HttpServletRequest request) throws Exception;

    OrderPaymentResult expressCheckoutPDPOCC(String cartId, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                             HttpServletRequest request) throws Exception;

    OrderPaymentResult expressCheckoutCartOCC(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                              HttpServletRequest request) throws Exception;

    Optional<ZoneDeliveryModeValueModel> getExpressDeliveryModePrice();

    boolean setDeliveryAddressForCart(final AddressData addressData, final String cartId);

    CartData createOrGetCartForExpressCheckout(String productCode);

    /**
     * Prepares the cart for express checkout with the given product.
     *
     * @param cartId      the cart identifier
     * @param productCode the product code to add
     * @param quantity    the quantity to add
     * @return the updated CartData
     * @throws CalculationException if cart recalculation fails
     */
    CartData prepareCartForExpressCheckoutWithProduct(String cartId, String productCode, Integer quantity) throws CalculationException;

    List<DeliveryModeData> getDeliveryModes(final String cartId);

    CartData setDeliveryModeForCart(final String deliveryModeCode, final String cartId) throws CalculationException;

    CartData getSessionCart();
}
