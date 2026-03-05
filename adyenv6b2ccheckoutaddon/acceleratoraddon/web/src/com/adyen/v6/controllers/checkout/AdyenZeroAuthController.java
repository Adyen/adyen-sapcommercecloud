package com.adyen.v6.controllers.checkout;

import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.controllers.checkout.dto.ZeroAuthRequest;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;

@Controller
@RequestMapping("/adyen")
public class AdyenZeroAuthController {

	private static final Logger LOGGER = Logger.getLogger(AdyenZeroAuthController.class);

	@Resource(name = "adyenCheckoutApiFacade")
	private AdyenCheckoutApiFacade adyenCheckoutApiFacade;

	@PostMapping(value = "/zero-auth", consumes = "application/json", produces = "application/json")
	@ResponseBody
	public ResponseEntity<String> zeroAuth(@RequestBody ZeroAuthRequest request) {
		try {
			final CheckoutPaymentMethod paymentMethod = ZeroAuthMapper.toCheckoutPaymentMethod(request);
			final PaymentResponse resp = adyenCheckoutApiFacade.processZeroAuthCard(paymentMethod);

			final String result = (resp != null && resp.getResultCode() != null)
					? resp.getResultCode().getValue()
					: null;

			return ResponseEntity.ok(result);

		} catch (IllegalArgumentException e) {
			LOGGER.warn("Invalid zero-auth request", e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

		} catch (ApiException e) {
			LOGGER.error("Adyen API exception during zero-auth", e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

		} catch (IOException e) {
			LOGGER.error("I/O error during zero-auth", e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

		} catch (Exception e) {
			LOGGER.error("Unexpected error during zero-auth", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
		}
	}
}