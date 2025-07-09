/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.backoffice.service;

public interface AdyenbackofficeService
{
	String getHybrisLogoUrl(String logoCode);

	void createLogo(String logoCode);
}
