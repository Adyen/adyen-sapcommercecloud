package com.adyen.v6.backoffice.editors;

import com.adyen.commerce.services.BillingPlatformAvailabilityService;
import com.hybris.cockpitng.editor.defaultenum.EnumValueResolver;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.HybrisEnumValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class BillingPlatformEnumValueResolverTest {

    private static final String VALUE_TYPE = "java.lang.Enum(BillingPlatform)";

    private static final HybrisEnumValue RECURLY = new TestBillingPlatform("RECURLY");
    private static final HybrisEnumValue CHARGEBEE = new TestBillingPlatform("CHARGEBEE");
    private static final HybrisEnumValue UNSUPPORTED = new TestBillingPlatform("UNSUPPORTED_PLATFORM");

    @Mock
    private EnumValueResolver enumValueResolverMock;
    @Mock
    private BillingPlatformAvailabilityService billingPlatformAvailabilityServiceMock;

    @InjectMocks
    private BillingPlatformEnumValueResolver testObj;

    @Test
    public void getAllValues_WhenNoConnectorLoaded_ShouldReturnEmptyList() {
        givenAllPlatforms(null);
        when(billingPlatformAvailabilityServiceMock.getAvailablePlatformCodes()).thenReturn(Collections.emptySet());

        assertTrue(testObj.getAllValues(VALUE_TYPE, null).isEmpty());
    }

    @Test
    public void getAllValues_WhenSomeConnectorsLoaded_ShouldReturnOnlyTheirPlatforms() {
        givenAllPlatforms(null);
        when(billingPlatformAvailabilityServiceMock.getAvailablePlatformCodes()).thenReturn(Set.of("RECURLY"));

        assertEquals(List.of(RECURLY), testObj.getAllValues(VALUE_TYPE, null));
    }

    @Test
    public void getAllValues_WhenCurrentPlatformHasNoConnector_ShouldStillReturnIt() {
        givenAllPlatforms(UNSUPPORTED);
        when(billingPlatformAvailabilityServiceMock.getAvailablePlatformCodes()).thenReturn(Set.of("CHARGEBEE"));

        assertEquals(List.of(CHARGEBEE, UNSUPPORTED), testObj.getAllValues(VALUE_TYPE, UNSUPPORTED));
    }

    @Test
    public void getAllValues_WhenDelegateReturnsNull_ShouldReturnEmptyList() {
        when(enumValueResolverMock.getAllValues(VALUE_TYPE, null)).thenReturn(null);
        when(billingPlatformAvailabilityServiceMock.getAvailablePlatformCodes()).thenReturn(Set.of("RECURLY"));

        assertTrue(testObj.getAllValues(VALUE_TYPE, null).isEmpty());
    }

    @Test
    public void getAllValues_ShouldReturnNewMutableListOnEveryCall() {
        givenAllPlatforms(null);
        when(billingPlatformAvailabilityServiceMock.getAvailablePlatformCodes()).thenReturn(Set.of("RECURLY", "CHARGEBEE"));

        final List<Object> first = testObj.getAllValues(VALUE_TYPE, null);
        final List<Object> second = testObj.getAllValues(VALUE_TYPE, null);

        assertNotSame(first, second);
        first.sort((left, right) -> ((HybrisEnumValue) left).getCode().compareTo(((HybrisEnumValue) right).getCode()));
        assertEquals(List.of(CHARGEBEE, RECURLY), first);
        assertEquals(List.of(RECURLY, CHARGEBEE), second);
    }

    private void givenAllPlatforms(final Object currentValue) {
        when(enumValueResolverMock.getAllValues(VALUE_TYPE, currentValue)).thenReturn(List.<Object>of(RECURLY, CHARGEBEE, UNSUPPORTED));
    }

    private static final class TestBillingPlatform implements HybrisEnumValue {

        private final String code;

        private TestBillingPlatform(final String code) {
            this.code = code;
        }

        @Override
        public String getType() {
            return "BillingPlatform";
        }

        @Override
        public String getCode() {
            return code;
        }
    }
}
