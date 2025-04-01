package com.adyen.commerce.controllers.api;

import com.adyen.commerce.response.ConfigurationResponse;
import com.adyen.commerce.response.RegionResponse;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.enums.CountryType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping(value = "/api/configuration")
public class AdyenConfigurationController {

    @Autowired
    private CheckoutFacade checkoutFacade;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private I18NFacade i18NFacade;

    @GetMapping("/shipping-address")
    public ResponseEntity<ConfigurationResponse> getConfiguration(Map map) {
        ConfigurationResponse configurationResponse = new ConfigurationResponse();

        configurationResponse.setCountries(checkoutFacade.getCountries(CountryType.SHIPPING));
        configurationResponse.setTitles(userFacade.getTitles());
        configurationResponse.setAnonymous(userFacade.isAnonymousUser());
        configurationResponse.setRegions(mapRegions(i18NFacade.getRegionsForAllCountries()));

        return ResponseEntity.ok(configurationResponse);
    }

    private List<RegionResponse> mapRegions(final Map<String, List<RegionData>> regions) {
        List<RegionResponse> result = new ArrayList<>();

        regions.forEach((k, v) -> {
            RegionResponse regionResponse = new RegionResponse(k, v);
            result.add(regionResponse);
        });

        return result;
    }

}
