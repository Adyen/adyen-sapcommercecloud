package com.adyen.v6.controllers.checkout;

import com.adyen.commerce.facades.impl.*;
import com.adyen.model.checkout.*;
import com.adyen.service.exception.*;
import com.adyen.v6.controllers.checkout.dto.*;
import com.adyen.v6.facades.impl.*;
import com.adyen.v6.model.*;
import org.apache.log4j.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;

@Controller
@RequestMapping("/adyen")
public class AdyenZeroAuthController {

	private static final Logger LOGGER = Logger.getLogger(AdyenZeroAuthController.class);

	@Resource(name = "adyenCheckoutFacade")
	private DefaultAdyenCheckoutApiFacade adyenCheckoutApiFacade;

	@PostMapping(value = "/zero-auth", consumes = "application/json", produces = "application/json")
	@ResponseBody
	public ResponseEntity<String> zeroAuth(@RequestBody ZeroAuthRequest request) {

		try {
			CheckoutPaymentMethod paymentMethod = ZeroAuthMapper.toCheckoutPaymentMethod(request);

			PaymentResponse resp = adyenCheckoutApiFacade.processZeroAuthCard(paymentMethod);

			return ResponseEntity.ok(resp.getResultCode().getValue());

		} catch (ApiException e) {
			LOGGER.error("Api exception: " + e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
		} catch (IOException e) {
			LOGGER.error("IOException: " + e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
		} catch (Exception e) {
			LOGGER.error("Exception: " + e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}
}