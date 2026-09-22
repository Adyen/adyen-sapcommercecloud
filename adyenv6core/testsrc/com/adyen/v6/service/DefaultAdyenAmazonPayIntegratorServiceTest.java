package com.adyen.v6.service;

import com.adyen.v6.enums.AmazonpayEnvironment;
import com.adyen.v6.enums.AmazonpayRegion;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenAmazonPayIntegratorServiceTest {

    private static final String FAKE_PUBLIC_KEY = "publicKey";
    private static final String CHECKOUT_SESSION_ID = "checkoutSessionId";
    @InjectMocks
    private DefaultAdyenAmazonPayIntegratorService testObj;
    @Mock
    private BaseStoreService baseStoreServiceMock;
    @Mock
    private BaseStoreModel baseStoreModelMock;

    @Test(expected = IllegalArgumentException.class)
    public void getAmazonPayTokenByCheckoutSessionId_shouldThrownLillegalArgumentException_whenCheckoutSessionIdIsNull() {
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(baseStoreModelMock.getAmazonpayEnvironment()).thenReturn(AmazonpayEnvironment.SANDBOX);
        when(baseStoreModelMock.getAmazonpayPublicKey()).thenReturn(FAKE_PUBLIC_KEY);
        when(baseStoreModelMock.getAmazonpayRegion()).thenReturn(AmazonpayRegion.EU);

        testObj.getAmazonPayTokenByCheckoutSessionId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAmazonPayTokenByCheckoutSessionId_shouldThrownLillegalArgumentException_whenCheckoutSessionIdIsEmpty() {
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(baseStoreModelMock.getAmazonpayEnvironment()).thenReturn(AmazonpayEnvironment.SANDBOX);
        when(baseStoreModelMock.getAmazonpayPublicKey()).thenReturn(FAKE_PUBLIC_KEY);
        when(baseStoreModelMock.getAmazonpayRegion()).thenReturn(AmazonpayRegion.EU);

        testObj.getAmazonPayTokenByCheckoutSessionId(StringUtils.EMPTY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAmazonPayTokenByCheckoutSessionId_shouldThrownLillegalArgumentException_whenCurrentBaseStoreIsNotSet() {
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(null);

        testObj.getAmazonPayTokenByCheckoutSessionId(StringUtils.EMPTY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAmazonPayTokenByCheckoutSessionId_shouldThrownLillegalArgumentException_whenAmazonEnvironmentIsNotSet() {
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(baseStoreModelMock.getAmazonpayEnvironment()).thenReturn(null);

        testObj.getAmazonPayTokenByCheckoutSessionId(StringUtils.EMPTY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAmazonPayTokenByCheckoutSessionId_shouldThrownLillegalArgumentException_whenAmazonPublicKeyIsNotSet() {
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(baseStoreModelMock.getAmazonpayPublicKey()).thenReturn(null);

        testObj.getAmazonPayTokenByCheckoutSessionId(StringUtils.EMPTY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getAmazonPayTokenByCheckoutSessionId_shouldThrownLillegalArgumentException_whenAmazonRegionIsNotSet() {
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(baseStoreModelMock.getAmazonpayRegion()).thenReturn(null);

        testObj.getAmazonPayTokenByCheckoutSessionId(StringUtils.EMPTY);
    }

    @Test
    public void getAmazonPayTokenByCheckoutSessionId_shouldReturnAnEmptyString_whenCheckoutSessionIdIsEmpty() {
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(baseStoreModelMock.getAmazonpayEnvironment()).thenReturn(AmazonpayEnvironment.SANDBOX);
        when(baseStoreModelMock.getAmazonpayPublicKey()).thenReturn(FAKE_PUBLIC_KEY);
        when(baseStoreModelMock.getAmazonpayRegion()).thenReturn(AmazonpayRegion.EU);

        final String result = testObj.getAmazonPayTokenByCheckoutSessionId(CHECKOUT_SESSION_ID);

        assertThat(result).isEmpty();
    }
}
