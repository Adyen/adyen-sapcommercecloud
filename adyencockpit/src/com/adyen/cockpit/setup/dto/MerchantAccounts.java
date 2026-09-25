package com.adyen.cockpit.setup.dto;

import java.util.List;

/** @param failure why the list could not be read, or {@code null} when it was. */
public record MerchantAccounts(List<MerchantAccountView> accounts, String failure)
{
}
