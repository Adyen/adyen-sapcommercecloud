/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.backoffice.setup;

import static com.adyen.backoffice.constants.AdyenbackofficeConstants.PLATFORM_LOGO_CODE;

import de.hybris.platform.core.initialization.SystemSetup;

import java.io.InputStream;

import com.adyen.backoffice.constants.AdyenbackofficeConstants;
import com.adyen.backoffice.service.AdyenbackofficeService;


@SystemSetup(extension = AdyenbackofficeConstants.EXTENSIONNAME)
public class AdyenbackofficeSystemSetup
{
	private final AdyenbackofficeService adyenbackofficeService;

	public AdyenbackofficeSystemSetup(final AdyenbackofficeService adyenbackofficeService)
	{
		this.adyenbackofficeService = adyenbackofficeService;
	}

	@SystemSetup(process = SystemSetup.Process.INIT, type = SystemSetup.Type.ESSENTIAL)
	public void createEssentialData()
	{
		adyenbackofficeService.createLogo(PLATFORM_LOGO_CODE);
	}

	private InputStream getImageStream()
	{
		return AdyenbackofficeSystemSetup.class.getResourceAsStream("/adyenbackoffice/sap-hybris-platform.png");
	}
}
