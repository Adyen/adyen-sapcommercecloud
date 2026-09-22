package com.adyen.v6.service;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import static com.adyen.v6.service.DefaultAdyenShopperIpResolverService.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdyenShopperIpResolverServiceTest {

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private Configuration configuration;

    @InjectMocks
    private DefaultAdyenShopperIpResolverService adyenShopperIpResolverService;

    private MockHttpServletRequest request;

    private static final String PROXY_LOCAL_IP = "127.0.0.1";


    @Before
    public void setUp() {
        when(configurationService.getConfiguration()).thenReturn(configuration);
        when(configuration.getString(CUSTOM_HEADER_PROPERTIES_KEY)).thenReturn(null);

        request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY_LOCAL_IP);
    }

    @Test
    public void testResolveShopperIpWithCustomHeader() {
        // Given
        final String customHeaderName = "custom-header";
        final String expectedIp = "192.168.1.1";
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(customHeaderName, expectedIp);
        request.addHeader(X_FORWARDED_FOR, "203.0.113.195, 70.41.3.18, 150.172.238.10");

        when(configuration.getString(CUSTOM_HEADER_PROPERTIES_KEY)).thenReturn(customHeaderName);

        // When
        final String result = adyenShopperIpResolverService.resolveShopperIp(request);

        // Then
        assertEquals(expectedIp, result);
    }

    @Test
    public void testResolveShopperIpWithOneValueHeader() {
        // Given
        final String headerName = X_REAL_IP;
        final String expectedIp = "10.0.0.1";
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(headerName, expectedIp);

        // When
        final String result = adyenShopperIpResolverService.resolveShopperIp(request);

        // Then
        assertEquals(expectedIp, result);
    }

    @Test
    public void testResolveShopperIpWithForwardedForHeader() {
        // Given
        final String forwardedForValue = "203.0.113.195, 70.41.3.18, 150.172.238.10";
        final String expectedIp = "203.0.113.195";
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(X_FORWARDED_FOR, forwardedForValue);

        // When
        final String result = adyenShopperIpResolverService.resolveShopperIp(request);

        // Then
        assertEquals(expectedIp, result);
    }

    @Test
    public void testResolveShopperIpWithRemoteAddr() {
        // Given
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // When
        final String result = adyenShopperIpResolverService.resolveShopperIp(request);

        // Then
        assertEquals(PROXY_LOCAL_IP, result);
    }

    @Test
    public void testPriorityWithMultipleHeadersPresent() {
        // Given
        final String customHeaderName = "custom-shopper-ip";
        final String customIp = "200.200.200.200";
        final String xRealIp = "100.100.100.100";
        final String forwardedForIp = "50.50.50.50";

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(customHeaderName, customIp);
        request.addHeader(X_REAL_IP, xRealIp);
        request.addHeader(X_FORWARDED_FOR, forwardedForIp);

        when(configuration.getString("adyen.checkout.shopperIpHeader")).thenReturn(customHeaderName);

        // When
        final String result = adyenShopperIpResolverService.resolveShopperIp(request);

        // Then
        assertEquals(customIp, result);
    }

    @Test
    public void testPriorityWhenCustomHeaderIsNotPresentButOtherHeadersAre() {
        // Given
        final String xRealIp = "100.100.100.100";
        final String forwardedForIp = "50.50.50.50";

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(X_REAL_IP, xRealIp);
        request.addHeader(X_FORWARDED_FOR, forwardedForIp);

        when(configuration.getString("adyen.checkout.shopperIpHeader")).thenReturn(null);

        // When
        final String result = adyenShopperIpResolverService.resolveShopperIp(request);

        // Then
        assertEquals(xRealIp, result);
    }
}