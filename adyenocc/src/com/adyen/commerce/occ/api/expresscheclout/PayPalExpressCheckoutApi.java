package com.adyen.commerce.occ.api.expresscheclout;

import com.adyen.commerce.occ.request.PayPalExpressCartRequest;
import com.adyen.commerce.occ.request.PayPalExpressPDPRequest;
import com.adyen.commerce.occ.request.PayPalIntermediateRequest;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaypalUpdateOrderRequest;
import com.adyen.model.checkout.PaypalUpdateOrderResponse;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;


@Tag(name = "Adyen")
public interface PayPalExpressCheckoutApi {

    @Operation(
            operationId = "placeOrderPayPalExpressPDP",
            summary = "Handle PayPal Express place order request from PDP",
            description = "Initiates a PayPal Express Checkout from the Product Detail Page (PDP). " +
                    "The request should contain PayPal details and product/cart information.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PayPal Express PDP request details.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PayPalExpressPDPRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Order placed successfully or further action required. Returns order confirmation or redirect details.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OCCPlaceOrderResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request - Invalid PayPal data, cart issue, or address validation failure."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error during order processing.")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<String> PayPalCartExpressCheckoutPDP(final HttpServletRequest request, @RequestBody String PayPalExpressPDPRequestString) throws Exception;

    @Operation(
            operationId = "placeOrderPayPalExpressCart",
            summary = "Handle PayPal Express place order request from Cart",
            description = "Initiates a PayPal Express Checkout from the Cart page. " +
                    "The request should contain PayPal details and cart information.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PayPal Express Cart request details.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PayPalExpressCartRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Order placed successfully or further action required. Returns order confirmation or redirect details.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OCCPlaceOrderResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request - Invalid PayPal data or cart issue."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error during order processing.")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<String> PayPalCartExpressCheckoutCart(final HttpServletRequest request, @RequestBody String PayPalExpressCartRequestString) throws Exception;

    @Operation(
            operationId = "submitPayPalExpressPDPOrder", // Changed for uniqueness
            summary = "Submit PayPal Express order details from PDP",
            description = "Submits the finalized PayPal payment details after customer approval on PayPal's site, for an order initiated from PDP.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Intermediate PayPal request details after customer approval.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PayPalIntermediateRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "PayPal payment submission successful. Returns Adyen's payment response.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = PaymentResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request - PayPal submission failed, possibly due to API error or invalid data."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error during submission processing.")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> onSubmitPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody String payPalIntermediateRequestString) throws Exception;

    @Operation(
            operationId = "updatePayPalExpressOrder",
            summary = "Update PayPal Express order details",
            description = "Updates an existing PayPal order, typically used for changing amounts or line items before final submission.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Request details for updating a PayPal order.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaypalUpdateOrderRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "PayPal order updated successfully. Returns the update response from Adyen.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = PaypalUpdateOrderResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request - PayPal order update failed."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error during order update processing.")
            }
    )
    @ApiBaseSiteIdAndUserIdParam
    public ResponseEntity<String> paypalUpdateOrder(final HttpServletRequest request, final HttpServletResponse response, @RequestBody String payPalpalUpdateOrderRequestString) throws Exception;
}
