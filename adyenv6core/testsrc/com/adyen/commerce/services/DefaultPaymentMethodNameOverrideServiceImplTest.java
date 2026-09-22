package com.adyen.commerce.services;

import com.adyen.commerce.services.impl.DefaultPaymentMethodNameOverrideServiceImpl;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.PaymentMethodsResponse;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.c2l.LanguageModel;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultPaymentMethodNameOverrideServiceImplTest {

    @InjectMocks
    private DefaultPaymentMethodNameOverrideServiceImpl testObj;

    @Mock
    private BaseStoreService baseStoreService;
    @Mock
    private CommonI18NService commonI18NService;
    @Mock
    private BaseStoreModel baseStoreModel;
    @Mock
    private LanguageModel languageModel;
    @Mock
    private LanguageModel languageModel2;

    private static final String PM_TYPE_CARD = "scheme";
    private static final String OVERRIDDEN_NAME = "Credit Card (Custom)";
    public static final String ORIGINAL_NAME = "Original Name";


    @Before
    public void setUp() {
        // Setup common stubbing used across most tests
        when(baseStoreService.getCurrentBaseStore()).thenReturn(baseStoreModel);
        when(commonI18NService.getCurrentLanguage()).thenReturn(languageModel);
    }

    @Test
    public void shouldOverrideNameWhenConfigExistsForLanguage() {
        // Given
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setType(PM_TYPE_CARD);
        paymentMethod.setName("Credit Card");

        PaymentMethodsResponse response = new PaymentMethodsResponse();
        response.setPaymentMethods(Collections.singletonList(paymentMethod));

        // Setup the nested Map structure: Map<Type, Map<Language, OverrideName>>
        Map<String, Map<LanguageModel, String>> config = new HashMap<>();
        Map<LanguageModel, String> languageMap = new HashMap<>();
        languageMap.put(languageModel, OVERRIDDEN_NAME);
        config.put(PM_TYPE_CARD, languageMap);

        when(baseStoreModel.getAdyenPaymentMethodNameConfig()).thenReturn(config);

        // When
        PaymentMethodsResponse result = testObj.overridePaymentMethodNamesFromConfig(response);

        // Then
        assertEquals("The payment method name should be updated with the value from config",
                OVERRIDDEN_NAME, result.getPaymentMethods().get(0).getName());
    }

    @Test
    public void shouldHandleEmptyConfigGracefully() {
        // Given
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setType(PM_TYPE_CARD);
        paymentMethod.setName(ORIGINAL_NAME);

        PaymentMethodsResponse response = new PaymentMethodsResponse();
        response.setPaymentMethods(Collections.singletonList(paymentMethod));

        // Empty config map
        when(baseStoreModel.getAdyenPaymentMethodNameConfig()).thenReturn(null);

        // When
        PaymentMethodsResponse result = testObj.overridePaymentMethodNamesFromConfig(response);

        // Then
        assertEquals(ORIGINAL_NAME, result.getPaymentMethods().get(0).getName());
    }

    @Test
    public void shouldNotOverrideIfLanguageMapDoesNotContainCurrentLanguage() {
        // Given
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setType(PM_TYPE_CARD);
        paymentMethod.setName(ORIGINAL_NAME);

        PaymentMethodsResponse response = new PaymentMethodsResponse();
        response.setPaymentMethods(Collections.singletonList(paymentMethod));

        Map<String, Map<LanguageModel, String>> config = new HashMap<>();
        // Map exists for type, but is empty (missing our current language)
        HashMap<LanguageModel, String> languageModelMap = new HashMap<>();
        languageModelMap.put(languageModel2, "New Name");

        config.put(PM_TYPE_CARD, languageModelMap);

        when(baseStoreModel.getAdyenPaymentMethodNameConfig()).thenReturn(config);

        // When
        PaymentMethodsResponse result = testObj.overridePaymentMethodNamesFromConfig(response);

        // Then
        assertEquals(ORIGINAL_NAME, result.getPaymentMethods().get(0).getName());
    }
}