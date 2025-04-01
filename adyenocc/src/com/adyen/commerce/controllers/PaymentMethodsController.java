/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.commerce.controllers;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v2")
@Tag(name = "Adyen")
public class PaymentMethodsController
{
    protected static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP" })
    @GetMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/checkout-configuration")
    @Operation(operationId = "getCheckoutConfiguration", summary = "Get checkout configuration", description =
            "Returns configuration for Adyen dropin component")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> getCheckoutConfiguration() throws ApiException, JsonProcessingException {
        String response = objectMapper.writeValueAsString(adyenCheckoutFacade.getReactCheckoutConfig());
        return ResponseEntity.ok().body(response);
    }

    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP" })
    @GetMapping(value = AdyenoccConstants.ADYEN_USER_PREFIX + "/checkout-configuration/express/PDP/{productCode}")
    @Operation(operationId = "getExpressPDPCheckoutConfiguration", summary = "Get express product page checkout configuration", description =
            "Returns configuration for express payments on PDP")
    @ApiBaseSiteIdAndUserIdParam
    public ResponseEntity<String> getExpressPDPCheckoutConfiguration(@PathVariable String productCode) throws ApiException, JsonProcessingException {
        String response = objectMapper.writeValueAsString(adyenCheckoutFacade.initializeExpressCheckoutPDPDataOCC(productCode));
        return ResponseEntity.ok().body(response);
    }

    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP" })
    @GetMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/checkout-configuration/express/cart")
    @Operation(operationId = "getExpressCartCheckoutConfiguration", summary = "Get express cart page checkout configuration", description =
            "Returns configuration for express payments on cart")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> getExpressCartCheckoutConfiguration() throws ApiException, JsonProcessingException, CalculationException {
        String response = objectMapper.writeValueAsString(adyenCheckoutFacade.initializeExpressCheckoutCartPageDataOCC());
        return ResponseEntity.ok().body(response);
    }
}
