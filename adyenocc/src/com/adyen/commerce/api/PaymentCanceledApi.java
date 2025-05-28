package com.adyen.commerce.api;

import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Adyen")
public interface PaymentCanceledApi {


    @Operation(operationId = "paymentCanceled", summary = "Handle payment canceled request", description =
            "Restores cart from order code and data in session")
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<Void> onCancel(@PathVariable String orderCode) throws InvalidCartException, CalculationException;
}
