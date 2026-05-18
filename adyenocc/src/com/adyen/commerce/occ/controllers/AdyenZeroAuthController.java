package com.adyen.commerce.occ.controllers;

import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.occ.mappers.ZeroAuthMapper;
import com.adyen.commerce.occ.request.ZeroAuthRequest;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/{baseSiteId}/adyen")
public class AdyenZeroAuthController {

    private static final String INVALID_REQUEST_MESSAGE = "Invalid zero-auth request";
    private static final String ZERO_AUTH_FAILED_MESSAGE = "Zero-auth request failed";
    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error";

    private static final Logger LOGGER = Logger.getLogger(AdyenZeroAuthController.class);

    @Resource(name = "adyenCheckoutApiFacade")
    private AdyenCheckoutApiFacade adyenCheckoutApiFacade;

    @PostMapping(value = "/zero-auth", consumes = "application/json", produces = "application/json")
    public ResponseEntity<String> zeroAuth(@RequestBody @Valid ZeroAuthRequest request) {
        try {
            CheckoutPaymentMethod paymentMethod = ZeroAuthMapper.toCheckoutPaymentMethod(request);
            PaymentResponse resp = adyenCheckoutApiFacade.processZeroAuthCard(paymentMethod);
            return ResponseEntity.ok(resp.toJson());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid zero-auth request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(INVALID_REQUEST_MESSAGE);
        } catch (ApiException e) {
            LOGGER.error("Adyen API exception during zero-auth", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ZERO_AUTH_FAILED_MESSAGE);
        } catch (IOException e) {
            LOGGER.error("I/O error during zero-auth", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ZERO_AUTH_FAILED_MESSAGE);
        } catch (Exception e) {
            LOGGER.error("Unexpected error during zero-auth", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(UNEXPECTED_ERROR_MESSAGE);
        }
    }
}
