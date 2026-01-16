package com.adyen.commerce.api.controllers.api;

import com.adyen.commerce.exception.AdyenControllerException;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;


@Controller
@RequestMapping(value = "/api/checkout")
public class AdyenDeliveryMethodsController {

    @Autowired
    private CheckoutFacade checkoutFacade;

    @RequireHardLogIn
    @GetMapping(value = "/delivery-methods", produces = "application/json")
    public ResponseEntity<List<? extends DeliveryModeData>> getAllDeliveryMethods() {
        return new ResponseEntity<>(checkoutFacade.getSupportedDeliveryModes(), HttpStatus.OK);
    }

    @RequireHardLogIn
    @PostMapping(value = "/select-delivery-method", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity selectDeliveryMethod(@RequestBody DeliveryMethodSelectionRequest request) {
        final String deliveryMethodCode = request.getDeliveryMethodCode();
        if (StringUtils.isNotEmpty(deliveryMethodCode)) {
            checkoutFacade.setDeliveryMode(deliveryMethodCode);
            return ResponseEntity.ok(checkoutFacade.getCheckoutCart());
        } else {
            throw new AdyenControllerException("checkout.deliveryMethod.notSelected");
        }
    }

    public static class DeliveryMethodSelectionRequest {
        private String deliveryMethodCode;

        public String getDeliveryMethodCode() {
            return deliveryMethodCode;
        }

        public void setDeliveryMethodCode(String deliveryMethodCode) {
            this.deliveryMethodCode = deliveryMethodCode;
        }
    }

}
