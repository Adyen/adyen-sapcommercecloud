package com.adyen.commerce.controllers;

import com.adyen.commerce.api.PaymentCanceledApi;
import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = AdyenoccConstants.ADYEN_USER_PREFIX)
@ApiVersion("v2")
public class PaymentCanceledController implements PaymentCanceledApi {

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Override
    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/payment-canceled/{orderCode}")
    public ResponseEntity<Void> onCancel(@PathVariable String orderCode) throws InvalidCartException, CalculationException {
        adyenCheckoutFacade.restoreCartFromOrderOCC(orderCode);
        return ResponseEntity.ok().build();
    }
}
