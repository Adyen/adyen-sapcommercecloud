package com.adyen.v6.service;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.List;

public class DefaultAdyenShopperIpResolverService implements AdyenShopperIpResolverService {
    private static final org.apache.log4j.Logger LOG = Logger.getLogger(DefaultAdyenShopperIpResolverService.class);

    static final String X_REAL_IP = "X-Real-IP";
    static final String CF_CONNECTING_ip = "CF-Connecting-IP";
    static final String TRUE_CLIENT_IP = "True-Client-IP";
    static final String FASTLY_CLIENT_IP = "Fastly-Client-IP";
    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    static final String CUSTOM_HEADER_PROPERTIES_KEY = "adyen.checkout.shopperIpHeader";

    private static final List<String> ONE_VALUE_HEADERS = List.of(X_REAL_IP, CF_CONNECTING_ip, TRUE_CLIENT_IP, FASTLY_CLIENT_IP);

    private ConfigurationService configurationService;

    public String resolveShopperIp(HttpServletRequest request) {

        logHeaders(request);

        String customShopperIpHeader = configurationService.getConfiguration().getString(CUSTOM_HEADER_PROPERTIES_KEY);

        if (StringUtils.isNotEmpty(customShopperIpHeader)) {
            String headerValue = request.getHeader(customShopperIpHeader);

            LOG.debug("Using custom shopper ip header: " + customShopperIpHeader + " with value: " + headerValue);

            return headerValue;
        }

        for (String header : ONE_VALUE_HEADERS) {
            String headerValue = request.getHeader(header);
            if (StringUtils.isNotEmpty(headerValue)) {
                LOG.debug("Using header: " + header + " with value: " + headerValue);

                return headerValue;
            }
        }

        String headerValue = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.isNotEmpty(headerValue)) {
            return headerValue.split(",")[0].trim();
        }

        LOG.debug("Using getRemoteAddr from request");

        return request.getRemoteAddr();
    }

    private void logHeaders(HttpServletRequest request) {
        StringBuilder headersLogBuilder = new StringBuilder();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValue = request.getHeaders(headerName);

            StringBuilder headerValueBuilder = new StringBuilder();
            while (headerValue.hasMoreElements()) {
                String value = headerValue.nextElement();

                headerValueBuilder.append(value);
                headerValueBuilder.append(",");
            }
            String headerValueString = headerValueBuilder.deleteCharAt(headerValueBuilder.length() - 1).toString();
            headersLogBuilder.append(headerName).append("=").append(headerValueString).append(System.lineSeparator());
        }

        LOG.debug("Request headers:" + System.lineSeparator() + headersLogBuilder.toString());
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
