package com.adyen.commerce.controllers;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.v6.facades.AdyenOrderFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commercewebservices.core.strategies.OrderCodeIdentificationStrategy;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = AdyenoccConstants.ADYEN_USER_PREFIX)
@ApiVersion("v2")
@Tag(name = "Adyen")
public class PaymentStatusController {

    @Autowired
    private OrderCodeIdentificationStrategy orderCodeIdentificationStrategy;

    @Autowired
    private AdyenOrderFacade adyenOrderFacade;


    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP"})
    @GetMapping(value = "/payment-status/{orderCode}")
    @Operation(operationId = "getPaymentStatus", summary = "Get order payment status.", description = "Returns payment status of order with given code.")
    @ApiBaseSiteIdAndUserIdParam
    public ResponseEntity<String> getPaymentStatus(
            @Parameter(description = "Order GUID (Globally Unique Identifier) or order CODE", required = true) @PathVariable final String orderCode) {
        try {
            String paymentStatus = adyenOrderFacade.getPaymentStatusOCC(orderCode);
            return ResponseEntity.ok(paymentStatus);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
