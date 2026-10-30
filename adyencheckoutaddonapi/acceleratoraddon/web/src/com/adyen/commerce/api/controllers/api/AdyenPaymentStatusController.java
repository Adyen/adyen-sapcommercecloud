package com.adyen.commerce.api.controllers.api;

import com.adyen.commerce.facades.AdyenOrderFacade;
import com.adyen.commerce.facades.AdyenPaymentOutcomeFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.servicelayer.session.SessionService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import org.springframework.http.HttpStatus;


@Controller
@RequestMapping("/api/checkout")
public class AdyenPaymentStatusController {
    private static final Logger LOG = Logger.getLogger(AdyenPaymentStatusController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private AdyenOrderFacade adyenOrderFacade;

    @Autowired
    private AdyenPaymentOutcomeFacade adyenPaymentOutcomeFacade;

    @RequireHardLogIn
    @PostMapping("/get-payment-status")
    public ResponseEntity<String> postIdForPaymentStatus(
            @RequestBody final String orderIdentifier) {

        final String checkoutGuid =
                sessionService.getAttribute(
                        WebConstants.ANONYMOUS_CHECKOUT_GUID);

        try {
            final String paymentStatus =
                    adyenPaymentOutcomeFacade
                            .getPaymentStatusAndRestoreCart(
                                    orderIdentifier,
                                    checkoutGuid);

            return ResponseEntity.ok(paymentStatus);

        } catch (final CalculationException
                       | InvalidCartException exception) {

            LOG.error(
                    "Could not restore cart for order "
                            + orderIdentifier,
                    exception);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();

        } catch (final Exception exception) {
            LOG.error(exception);
            return ResponseEntity.badRequest().build();
        }
    }

    @RequireHardLogIn
    @PostMapping("/get-order-code")
    public ResponseEntity<String> postGUIDForOrderCode(@RequestBody String orderGUID) {
        String checkoutGuid = sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT_GUID);

        try {
            String paymentStatus = adyenOrderFacade.getOrderCodeForGUID(orderGUID, checkoutGuid);
            return ResponseEntity.ok().body(paymentStatus);
        } catch (Exception exception) {
            LOG.error(exception);
            return ResponseEntity.badRequest().build();
        }
    }
}
