package com.adyen.commerce.api;

import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Adyen")
public interface PaymentStatusApi {


    @Operation(operationId = "getPaymentStatus", summary = "Get order payment status.", description = "Returns payment status of order with given code.")
    @ApiBaseSiteIdAndUserIdParam
    ResponseEntity<String> getPaymentStatus(
            @Parameter(description = "Order GUID (Globally Unique Identifier) or order CODE", required = true) @PathVariable final String orderCode) ;
}
