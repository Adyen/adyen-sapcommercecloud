package com.adyen.v6.controllers.checkout;

import com.adyen.v6.controllers.checkout.dto.CartDataDTO;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;


@Controller
@RequestMapping("/express-checkout/configure/")
public class AdyenExpressCheckoutController {

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Autowired
    private CartFacade cartFacade;

    @GetMapping("cart")
    public ResponseEntity<CartDataDTO> getCartForExpressCheckout(final HttpServletRequest request, final HttpServletResponse response){
        CartData cartForExpressCheckout = cartFacade.getSessionCart();
        return ResponseEntity.ok(populateCartDataDto(cartForExpressCheckout));
    }

    @PostMapping("create-cart/{productCode}")
    public ResponseEntity<CartDataDTO> createCartForExpressCheckout(final HttpServletRequest request, final HttpServletResponse response, @PathVariable("productCode")  String productCode){
        CartData cartForExpressCheckout = adyenExpressCheckoutFacade.createOrGetCartForExpressCheckout(productCode);
        return ResponseEntity.ok(populateCartDataDto(cartForExpressCheckout));
    }

    @PostMapping("{cartId}/addresses/delivery")
    @ResponseBody
    public ResponseEntity createCartDeliveryAddress(final HttpServletRequest request, final HttpServletResponse response, @PathVariable("cartId") String cartId, final @RequestBody AddressData addressData)
    {
        adyenExpressCheckoutFacade.setDeliveryAddressForCart(addressData, cartId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("{cartId}/product/{productCode}/quantity/{quantity}")
    public ResponseEntity setProductForExpressCheckout(final HttpServletRequest request, final HttpServletResponse response, @PathVariable("cartId")  String cartId, @PathVariable("productCode")  String productCode, @PathVariable("quantity") Integer quantity) throws CalculationException {
        adyenExpressCheckoutFacade.prepareCartForExpressCheckoutWithProduct(cartId, productCode, quantity);
        return ResponseEntity.ok().build();
    }

    @GetMapping("{cartId}/delivery-methods")
    public ResponseEntity<List<DeliveryModeData>> getDeliveryMethods(final HttpServletRequest request, final HttpServletResponse response, @PathVariable("cartId")  String cartId){
        return ResponseEntity.ok().body(adyenExpressCheckoutFacade.getDeliveryModes(cartId));
    }

    @PostMapping("{cartId}/delivery-method/{deliveryMethodId}")
    public ResponseEntity<CartDataDTO> setDeliveryMethod(final HttpServletRequest request, final HttpServletResponse response, @PathVariable("cartId")  String cartId, @PathVariable("deliveryMethodId") String deliveryMethodId) throws CalculationException {
        CartData cartForExpressCheckout = adyenExpressCheckoutFacade.setDeliveryModeForCart(deliveryMethodId, cartId);
        return ResponseEntity.ok(populateCartDataDto(cartForExpressCheckout));
    }

    private static CartDataDTO populateCartDataDto(CartData cartForExpressCheckout) {
        CartDataDTO cartDataDTO = new CartDataDTO();
        cartDataDTO.setCode(cartForExpressCheckout.getCode());
        cartDataDTO.setTotalPriceWithTax(cartForExpressCheckout.getTotalPriceWithTax());
        return cartDataDTO;
    }

}
