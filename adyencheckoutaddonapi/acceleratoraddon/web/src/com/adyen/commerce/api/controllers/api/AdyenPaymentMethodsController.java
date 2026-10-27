package com.adyen.commerce.api.controllers.api;

import com.adyen.service.exception.ApiException;
import com.adyen.v6.dto.CheckoutConfigDTO;
import com.adyen.v6.dto.DisclaimerConfigurationDTO;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequestMapping(value = "/api/checkout")
public class AdyenPaymentMethodsController {

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @RequireHardLogIn
    @GetMapping(value = "/payment-methods-configuration")
    public ResponseEntity<CheckoutConfigDTO> getPaymentMethodsConfiguration(
            @RequestParam(name = "shopperCountryCode", required = false) final String shopperCountryCode) throws ApiException {
        return ResponseEntity.ok().body(adyenCheckoutFacade.getReactCheckoutConfig(shopperCountryCode));
    }

    @RequireHardLogIn
    @GetMapping(value = "/disclaimer-configuration")
    public ResponseEntity<DisclaimerConfigurationDTO> getDisclaimerConfiguration(
            @RequestParam(name = "shopperCountryCode", required = false) final String shopperCountryCode) {
        return ResponseEntity.ok().body(adyenCheckoutFacade.getDisclaimerConfiguration(shopperCountryCode));
    }
}
