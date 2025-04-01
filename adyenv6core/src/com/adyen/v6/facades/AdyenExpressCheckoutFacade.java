package com.adyen.v6.facades;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeValueModel;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface AdyenExpressCheckoutFacade {

    PaymentResponse expressCheckoutPDP(String productCode, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                       HttpServletRequest request) throws Exception ;

    PaymentResponse expressCheckoutCart(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                        HttpServletRequest request) throws Exception;

    OrderData expressCheckoutPDPOCC(String productCode, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                    HttpServletRequest request) throws Exception;

    OrderData expressCheckoutCartOCC(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                     HttpServletRequest request) throws Exception;

    Optional<ZoneDeliveryModeValueModel> getExpressDeliveryModePrice();

}
