package com.adyen.commerce.occ.controllers;


import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.data.DataCollectionConfiguration;
import com.adyen.v6.facades.AdyenDataCollectionFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v2")
@Tag(name = "Adyen")
public class AdyenDataCollectionController {

    @Autowired
    private AdyenDataCollectionFacade adyenDataCollectionFacade;

    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP" })
    @GetMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/data-collection-configuration")
    @Operation(operationId = "getDataCollectionConfiguration", summary = "Get data collection configuration", description =
            "Returns configuration for Adyen data collection component")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<DataCollectionConfiguration> getDataCollectionConfiguration() {
        DataCollectionConfiguration dataCollectionConfiguration = adyenDataCollectionFacade.getDataCollectionConfiguration();

        return ResponseEntity.ok(dataCollectionConfiguration);
    }
}
