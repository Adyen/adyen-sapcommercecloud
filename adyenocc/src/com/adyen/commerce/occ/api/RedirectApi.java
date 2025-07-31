package com.adyen.commerce.occ.api;

import com.adyen.model.checkout.PaymentDetailsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;

@Tag(name = "Adyen")
public interface RedirectApi {


    @Operation(operationId = "adyenRedirect", summary = "Handle redirect payment method", description =
            "Handles return after payment method redirect flow returns")
    String authorizeRedirectPaymentGet(final HttpServletRequest request);

    @Operation(operationId = "adyenRedirect", summary = "Handle redirect payment method", description =
            "Handles return after payment method redirect flow returns")
    String authorizeRedirectPaymentPost(@Parameter(description = "Payment details data", required = true) @RequestBody PaymentDetailsRequest detailsRequest);
}
