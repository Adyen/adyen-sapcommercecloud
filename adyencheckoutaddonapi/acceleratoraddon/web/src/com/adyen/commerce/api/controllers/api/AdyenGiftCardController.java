package com.adyen.commerce.api.controllers.api;

import com.adyen.commerce.controllerbase.GiftCardControllerBase;
import com.adyen.commerce.facades.AdyenGiftCardFacade;
import com.adyen.commerce.request.GiftCardBalanceRequest;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "/api/giftcard")
public class AdyenGiftCardController extends GiftCardControllerBase {
    
    @Autowired
    private AdyenGiftCardFacade adyenGiftCardFacade;
    
    /**
     * Check gift card balance for partial payments
     * This endpoint is called to verify the available balance on a gift card before processing
     *
     * @param request The gift card balance request containing card details and amount
     * @return Response containing available balance and transaction limit
     */
    @RequireHardLogIn
    @PostMapping(value = "/balance", produces = "application/json")
    public ResponseEntity<?> checkGiftCardBalance(@RequestBody GiftCardBalanceRequest request) {
        return super.checkGiftCardBalance(request);
    }
    
    @Override
    public AdyenGiftCardFacade getAdyenGiftCardFacade() {
        return adyenGiftCardFacade;
    }
}