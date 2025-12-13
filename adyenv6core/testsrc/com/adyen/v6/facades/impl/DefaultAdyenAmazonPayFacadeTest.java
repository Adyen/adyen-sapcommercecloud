package com.adyen.v6.facades.impl;

import com.adyen.v6.service.AdyenAmazonPayIntegratorService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.site.BaseSiteService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenAmazonPayFacadeTest {
    private static final String AMAZON_PAY_TOKEN = "amazonPayToken";
    private static final String RELATIVE_URL = "relativeUrl";
    private static final String ABSOLUTE_URL = "absoluteUrl";
    private final String AMAZONPAY_CHECKOUT_SESSION_ID = "amazonpayCheckoutSessionId";

    @InjectMocks
    private DefaultAdyenAmazonPayFacade testObj;
    @Mock
    private AdyenAmazonPayIntegratorService adyenAmazonPayIntegratorServiceMock;
    @Mock
    private BaseSiteService baseSiteServiceMock;
    @Mock
    private SiteBaseUrlResolutionService siteBaseUrlResolutionServiceMock;

    @Mock
    private BaseSiteModel baseSiteModelMock;

    @Test
    public void getAmazonPayToken_shouldReturnTheAmazonPayToken() {
        when(adyenAmazonPayIntegratorServiceMock.getAmazonPayTokenByCheckoutSessionId(AMAZONPAY_CHECKOUT_SESSION_ID)).thenReturn(AMAZON_PAY_TOKEN);

        final String result = testObj.getAmazonPayToken(AMAZONPAY_CHECKOUT_SESSION_ID);

        assertThat(result).isEqualTo(AMAZON_PAY_TOKEN);
    }

    @Test
    public void getReturnUrl_shouldReturnUrl() {
        when(baseSiteServiceMock.getCurrentBaseSite()).thenReturn(baseSiteModelMock);
        when(siteBaseUrlResolutionServiceMock.getWebsiteUrlForSite(baseSiteModelMock, true, RELATIVE_URL)).thenReturn(ABSOLUTE_URL);

        final String result = testObj.getReturnUrl(RELATIVE_URL);

        assertThat(result).isEqualTo(ABSOLUTE_URL);
    }
}
