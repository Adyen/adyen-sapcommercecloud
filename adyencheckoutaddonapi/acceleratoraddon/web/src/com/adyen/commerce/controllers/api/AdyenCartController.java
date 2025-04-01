package com.adyen.commerce.controllers.api;

import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "/api/checkout")
public class AdyenCartController {

    @Autowired
    private CartFacade cartFacade;

    @GetMapping(value = "/cart-data", produces = "application/json")
    public ResponseEntity<CartData> getCartData() {
        CartData sessionCart = cartFacade.getSessionCart();
        return ResponseEntity.ok(sessionCart);
    }
}
