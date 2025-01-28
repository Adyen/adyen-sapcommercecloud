package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PayPalDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenPayPalExpressCheckoutFacade;
import com.adyen.v6.request.PayPalExpressCartRequest;
import com.adyen.v6.request.PayPalExpressPDPRequest;
import com.adyen.v6.request.PayPalExpressSubmitPDPRequest;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.acceleratorstorefrontcommons.security.GUIDCookieStrategy;
import de.hybris.platform.site.BaseSiteService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/express-checkout/paypal/")
public class AdyenPayPalExpressCheckoutController extends AdyenExpressCheckoutControllerBase {
    private static final Logger LOG = Logger.getLogger(AdyenPayPalExpressCheckoutController.class);

    @Autowired
    private AdyenPayPalExpressCheckoutFacade adyenPayPalExpressCheckoutFacade;

    @Autowired
    private GUIDCookieStrategy guidCookieStrategy;

    @Autowired
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @Autowired
    private BaseSiteService baseSiteService;

    @PostMapping("submit/PDP")
    public ResponseEntity onSubmitPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalExpressSubmitPDPRequest payPalSubmitRequest) throws Exception {
        PayPalDetails payPalDetails = payPalSubmitRequest.getPayPalDetails();
        PaymentRequest paymentRequest = new PaymentRequest();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        payPalDetails.setSubtype(PayPalDetails.SubtypeEnum.EXPRESS);

        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));
        paymentRequest.setReturnUrl(getReturnUrl(PayPalDetails.TypeEnum.PAYPAL.getValue()));

        try {
            PayPalExpressSubmitResponse paymentResponse = adyenPayPalExpressCheckoutFacade.onPayPalPDPSubmit(paymentRequest, payPalSubmitRequest.getProductCode());
            return new ResponseEntity<>(paymentResponse, HttpStatus.OK);

        } catch (ApiException e){
            LOG.error(e.getError());
            LOG.error(e.getMessage());

            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

    }

    @PostMapping("submit/cart")
    public ResponseEntity onSubmitCart(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalDetails payPalDetails) throws Exception {
        PaymentRequest paymentRequest = new PaymentRequest();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        payPalDetails.setSubtype(PayPalDetails.SubtypeEnum.EXPRESS);

        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));
        paymentRequest.setReturnUrl(getReturnUrl(PayPalDetails.TypeEnum.PAYPAL.getValue()));

        try {
            PaymentResponse paymentResponse = adyenPayPalExpressCheckoutFacade.onPayPalCartSubmit(paymentRequest);
            return new ResponseEntity<>(paymentResponse, HttpStatus.OK);

        } catch (ApiException e){
            LOG.error(e.getError());
            LOG.error(e.getMessage());

            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

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
