package com.adyen.commerce.controllers;

import com.adyen.commerce.api.PaymentStatusApi;
import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.v6.facades.AdyenOrderFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commercewebservices.core.strategies.OrderCodeIdentificationStrategy;
import io.swagger.v3.oas.annotations.Parameter;
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
public class PaymentStatusController implements PaymentStatusApi {

    @Autowired
    private OrderCodeIdentificationStrategy orderCodeIdentificationStrategy;

    @Autowired
    private AdyenOrderFacade adyenOrderFacade;


    @Override
    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP"})
    @GetMapping(value = "/payment-status/{orderCode}")
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
