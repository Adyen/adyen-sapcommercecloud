package com.adyen.backoffice.service.impl;

import com.adyen.backoffice.dto.MerchantResponseWsDTO;
import com.adyen.backoffice.service.AdyenManagementService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;

public class AdyenManagementServiceImpl implements AdyenManagementService {

    @Resource(name = "configurationService")
    private ConfigurationService configurationService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public MerchantResponseWsDTO getMerchants(final Integer pageSize, final Integer pageNumber) {
        final String endpoint = getConfigurationService().getConfiguration().getString("adyen.management.api.endpoint");
        final String apiKey = getConfigurationService().getConfiguration().getString("adyen.management.api.key");

        final HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);

        final HttpEntity<String> entity = new HttpEntity<>(headers);

        final UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(endpoint)
                .queryParam("pageSize", pageSize)
                .queryParam("pageNumber", pageNumber);

        final ResponseEntity<MerchantResponseWsDTO> response = restTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.GET,
                entity,
                MerchantResponseWsDTO.class
        );

        return response.getBody();
    }


    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}