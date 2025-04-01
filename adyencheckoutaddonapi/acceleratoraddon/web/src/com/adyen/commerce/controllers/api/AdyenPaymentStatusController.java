package com.adyen.commerce.controllers.api;

import com.adyen.v6.facades.AdyenOrderFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.servicelayer.session.SessionService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/api/checkout")
public class AdyenPaymentStatusController {
    private static final Logger LOG = Logger.getLogger(AdyenPaymentStatusController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private AdyenOrderFacade adyenOrderFacade;

    @RequireHardLogIn
    @PostMapping("/get-payment-status")
    public ResponseEntity<String> postIdForPaymentStatus(@RequestBody String orderCode) {
        String checkoutGuid = sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT_GUID);

        try {
            String paymentStatus = adyenOrderFacade.getPaymentStatus(orderCode, checkoutGuid);
            return ResponseEntity.ok().body(paymentStatus);
        } catch (Exception exception) {
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
