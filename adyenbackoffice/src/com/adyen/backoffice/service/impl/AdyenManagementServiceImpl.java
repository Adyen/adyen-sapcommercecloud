package com.adyen.backoffice.service.impl;

import com.adyen.backoffice.dto.MerchantDataWsDTO;
import com.adyen.backoffice.dto.MerchantResponseWsDTO;
import com.adyen.backoffice.dto.PaymentMethodResponseWsDTO;
import com.adyen.backoffice.dto.StoreResponseWsDTO;
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

    @Override
    public MerchantDataWsDTO getMerchantById(final String merchantId) {
        final String endpoint = getConfigurationService().getConfiguration().getString("adyen.management.api.endpoint");
        final String apiKey = getConfigurationService().getConfiguration().getString("adyen.management.api.key");

        final HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);

        final HttpEntity<String> entity = new HttpEntity<>(headers);

        final String merchantEndpoint = endpoint + "/" + merchantId;

        final ResponseEntity<MerchantDataWsDTO> response = restTemplate.exchange(
                merchantEndpoint,
                HttpMethod.GET,
                entity,
                MerchantDataWsDTO.class
        );

        return response.getBody();
    }

    @Override
    public StoreResponseWsDTO getStoresByMerchantId(final String merchantId, final Integer pageSize, final Integer pageNumber) {
        final String endpoint = getConfigurationService().getConfiguration().getString("adyen.management.api.endpoint");
        final String apiKey = getConfigurationService().getConfiguration().getString("adyen.management.api.key");

        final HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);

        final HttpEntity<String> entity = new HttpEntity<>(headers);

        // Build the stores endpoint URL
        final String storesEndpoint = endpoint + "/" + merchantId + "/stores";

        final UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(storesEndpoint);
        
        // Add pagination parameters if provided
        if (pageSize != null) {
            uriBuilder.queryParam("pageSize", pageSize);
        }
        if (pageNumber != null) {
            uriBuilder.queryParam("pageNumber", pageNumber);
        }

        final ResponseEntity<StoreResponseWsDTO> response = restTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.GET,
                entity,
                StoreResponseWsDTO.class
        );

        return response.getBody();
    }

    @Override
    public PaymentMethodResponseWsDTO getAllPaymentMethods(final String merchantId, final String storeId, final String businessLineId, final Integer pageSize, final Integer pageNumber) {
        final String endpoint = getConfigurationService().getConfiguration().getString("adyen.management.api.endpoint");
        final String apiKey = getConfigurationService().getConfiguration().getString("adyen.management.api.key");

        final HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);

        final HttpEntity<String> entity = new HttpEntity<>(headers);

        // Build the payment method settings endpoint URL
        final String paymentMethodsEndpoint = endpoint + "/" + merchantId + "/paymentMethodSettings";

        final UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(paymentMethodsEndpoint);
        
        // Add optional query parameters if provided
        if (storeId != null) {
            uriBuilder.queryParam("storeId", storeId);
        }
        if (businessLineId != null) {
            uriBuilder.queryParam("businessLineId", businessLineId);
        }
        if (pageSize != null) {
            uriBuilder.queryParam("pageSize", pageSize);
        }
        if (pageNumber != null) {
            uriBuilder.queryParam("pageNumber", pageNumber);
        }

        final ResponseEntity<PaymentMethodResponseWsDTO> response = restTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.GET,
                entity,
                PaymentMethodResponseWsDTO.class
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