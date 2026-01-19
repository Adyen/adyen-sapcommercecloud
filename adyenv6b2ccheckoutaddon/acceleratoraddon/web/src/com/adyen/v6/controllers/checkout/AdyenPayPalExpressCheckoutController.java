package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.*;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenPayPalExpressCheckoutFacade;
import com.adyen.v6.request.*;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.acceleratorstorefrontcommons.security.GUIDCookieStrategy;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.site.BaseSiteService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

@Controller
@RequestMapping("/express-checkout/paypal/")
public class AdyenPayPalExpressCheckoutController extends AdyenExpressCheckoutControllerBase {
    private static final Logger LOG = Logger.getLogger(AdyenPayPalExpressCheckoutController.class);

    @Resource
    private AdyenPayPalExpressCheckoutFacade adyenPayPalExpressCheckoutFacade;

    @Resource
    private GUIDCookieStrategy guidCookieStrategy;

    @Resource
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @Resource
    private BaseSiteService baseSiteService;

    @PostMapping("submit/PDP")
    public ResponseEntity onSubmitPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalExpressSubmitPDPRequest payPalSubmitRequest) throws Exception {
        PayPalDetails payPalDetails = payPalSubmitRequest.getPayPalDetails();
        PaymentRequest paymentRequest = new PaymentRequest();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        payPalDetails.setSubtype(PayPalDetails.SubtypeEnum.EXPRESS);

        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));
        paymentRequest.setReturnUrl(getReturnUrl(PayPalDetails.TypeEnum.PAYPAL.getValue()));

        PayPalExpressSubmitResponse paymentResponse = adyenPayPalExpressCheckoutFacade.onPayPalPDPSubmit(request, paymentRequest, payPalSubmitRequest.getProductCode());
        return new ResponseEntity<>(paymentResponse, HttpStatus.OK);

    }

    @PostMapping("submit/cart")
    public ResponseEntity onSubmitCart(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalDetails payPalDetails) throws Exception {
        PaymentRequest paymentRequest = new PaymentRequest();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        payPalDetails.setSubtype(PayPalDetails.SubtypeEnum.EXPRESS);

        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));
        paymentRequest.setReturnUrl(getReturnUrl(PayPalDetails.TypeEnum.PAYPAL.getValue()));

        PaymentResponse paymentResponse = adyenPayPalExpressCheckoutFacade.onPayPalCartSubmit(request, paymentRequest);
        return new ResponseEntity<>(paymentResponse, HttpStatus.OK);
    }

    @PostMapping("PDP")
    public ResponseEntity payPalExpressPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalExpressPDPRequest paypalExpressPDPRequest) throws Exception {

        adyenPayPalExpressCheckoutFacade.onPayPalAuthorizedPDP(paypalExpressPDPRequest.getCartGuid(),
                paypalExpressPDPRequest.getAddressData(), Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL);

        guidCookieStrategy.setCookie(request, response);

        return ResponseEntity.ok().build();
    }

    @PostMapping("cart")
    public ResponseEntity paypalCartExpressCheckout(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalExpressCartRequest paypalExpressCartRequest) throws Exception {

        adyenPayPalExpressCheckoutFacade.onPayPalAuthorizedCart(paypalExpressCartRequest.getAddressData(), Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL);

        guidCookieStrategy.setCookie(request, response);

        return ResponseEntity.ok().build();
    }

    @PostMapping("shipping-address")
    public ResponseEntity<PaypalUpdateOrderResponse> paypalSetShippingAddress(@RequestBody PayPalExpressShippingAddressRequest payPalExpressShippingAddressRequest) throws IOException, ApiException, DuplicateUidException, CalculationException {
        PaypalUpdateOrderResponse paypalUpdateOrderResponse = adyenPayPalExpressCheckoutFacade.updateShippingAddress(
                payPalExpressShippingAddressRequest.getAddressData(),
                payPalExpressShippingAddressRequest.getPspReference(),
                payPalExpressShippingAddressRequest.getPaymentData(),
                payPalExpressShippingAddressRequest.getCartGuid());

        return ResponseEntity.ok(paypalUpdateOrderResponse);
    }

    @PostMapping("shipping-method")
    public ResponseEntity<PaypalUpdateOrderResponse> paypalSetShippingMode(@RequestBody PayPalExpressShippingMethodRequest payPalExpressShippingMethodRequest) throws CalculationException, IOException, ApiException {
        PaypalUpdateOrderResponse paypalUpdateOrderResponse = adyenPayPalExpressCheckoutFacade.updateShippingMethod(payPalExpressShippingMethodRequest.getShippingMethodCode(),
                payPalExpressShippingMethodRequest.getPspReference(), payPalExpressShippingMethodRequest.getPaymentData(),
                payPalExpressShippingMethodRequest.getCartGuid());

        return ResponseEntity.ok(paypalUpdateOrderResponse);

    }

    private <T extends PayPalExpressCartRequest> PaymentRequest getPaymentRequest(T request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        PayPalDetails paypalDetails = request.getPayPalDetails();
        paypalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        paypalDetails.setSubtype(PayPalDetails.SubtypeEnum.EXPRESS);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(paypalDetails));
        paymentRequest.setReturnUrl(getReturnUrl(PayPalDetails.TypeEnum.PAYPAL.getValue()));
        return paymentRequest;
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = Exception.class)
    public void adyenComponentExceptionHandler(Exception e) {
        if (e instanceof ApiException) {
            LOG.error("Api Exception: " + ((ApiException) e).getResponseBody());
        }
        LOG.error("Exception during PaypalExpress processing", e);
    }

    @Override
    public BaseSiteService getBaseSiteService() {
        return baseSiteService;
    }

    @Override
    public SiteBaseUrlResolutionService getSiteBaseUrlResolutionService() {
        return siteBaseUrlResolutionService;
    }
}
