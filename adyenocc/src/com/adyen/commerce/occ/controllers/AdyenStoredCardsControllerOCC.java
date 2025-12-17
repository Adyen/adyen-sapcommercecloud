package com.adyen.commerce.occ.controllers;

import com.adyen.model.recurring.RecurringDetail;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.user.data.CustomerData;
import de.hybris.platform.webservicescommons.cache.CacheControl;
import de.hybris.platform.webservicescommons.cache.CacheControlDirective;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.List;

import static com.adyen.commerce.constants.AdyenoccConstants.ADYEN_USER_PREFIX;

@Controller
@RequestMapping(value = ADYEN_USER_PREFIX + "/stored-cards")
@CacheControl(directive = CacheControlDirective.NO_CACHE)
@Tag(name = "Stored Cards")
public class AdyenStoredCardsControllerOCC {

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;
    @Autowired
    private CustomerFacade customerFacade;

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getStoredCards", summary = "Returns stored card for user", description =
            "Returns stored card for use")
    @ApiBaseSiteIdAndUserIdParam
    public ResponseEntity<List<RecurringDetail>> getStoredCards() throws IOException, ApiException {
        CustomerData currentCustomer = customerFacade.getCurrentCustomer();
        List<RecurringDetail> storedCards = adyenCheckoutFacade.getAdyenPaymentService().getStoredCards(currentCustomer.getCustomerId());
        return ResponseEntity.ok(storedCards);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @DeleteMapping(path = "/{id}")
    @Operation(operationId = "getStoredCards", summary = "Returns stored card for user", description =
            "Returns stored card for use")
    @ApiBaseSiteIdAndUserIdParam
    public ResponseEntity<Object> removeStoredCard(@PathVariable String id) throws Exception {
        CustomerData currentCustomer = customerFacade.getCurrentCustomer();
        adyenCheckoutFacade.getAdyenPaymentService().disableStoredCard(currentCustomer.getCustomerId(),id);
        return ResponseEntity.ok().build();
    }
}