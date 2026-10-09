package com.adyen.v6.backoffice.editors;

import com.adyen.commerce.services.BillingPlatformAvailabilityService;
import com.hybris.cockpitng.editor.defaultenum.EnumValueResolver;
import de.hybris.platform.core.HybrisEnumValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Offers only the billing platforms whose connector extension is loaded.
 * <p>
 * The store's current platform is always offered, even when its connector is gone, so the editor still shows
 * what is saved instead of an empty field.
 */
public class BillingPlatformEnumValueResolver implements EnumValueResolver {

    private EnumValueResolver enumValueResolver;
    private BillingPlatformAvailabilityService billingPlatformAvailabilityService;

    @Override
    public List<Object> getAllValues(final String valueType, final Object value) {
        final Set<String> availableCodes = billingPlatformAvailabilityService.getAvailablePlatformCodes();
        final List<Object> allValues = enumValueResolver.getAllValues(valueType, value);

        // A new mutable list on every call: the enum editor sorts the returned list in place.
        final List<Object> offeredValues = new ArrayList<>();
        if (allValues != null) {
            for (final Object candidate : allValues) {
                if (isOffered(candidate, value, availableCodes)) {
                    offeredValues.add(candidate);
                }
            }
        }
        return offeredValues;
    }

    protected boolean isOffered(final Object candidate, final Object currentValue, final Set<String> availableCodes) {
        if (candidate == null) {
            return false;
        }
        if (Objects.equals(candidate, currentValue)) {
            return true;
        }
        return candidate instanceof HybrisEnumValue enumValue && availableCodes.contains(enumValue.getCode());
    }

    public void setEnumValueResolver(final EnumValueResolver enumValueResolver) {
        this.enumValueResolver = enumValueResolver;
    }

    public void setBillingPlatformAvailabilityService(final BillingPlatformAvailabilityService billingPlatformAvailabilityService) {
        this.billingPlatformAvailabilityService = billingPlatformAvailabilityService;
    }
}
