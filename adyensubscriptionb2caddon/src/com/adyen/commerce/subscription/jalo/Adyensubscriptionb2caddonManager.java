/*
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.subscription.jalo;

import de.hybris.platform.jalo.extension.Extension;

/**
 * The extension manager named by this addon's extensioninfo.xml. It does nothing; it has to exist.
 *
 * <p>A generated coremodule declared without this class in source makes the platform refuse to load the
 * extension at start-up with {@code ClassNotFoundException} for exactly this name. The build does not
 * catch it, and the symptom is not an error page: the CMS page and the addon's JSP still render, because
 * both are data and files rather than extension code, so the page comes up empty with no controller
 * behind it.</p>
 *
 * <p>It derives straight from {@link Extension} rather than a {@code Generated…Manager} because this addon
 * has no items.xml worth the name, so the generator produces nothing to extend.</p>
 */
public class Adyensubscriptionb2caddonManager extends Extension
{
	@Override
	public String getName()
	{
		return "adyensubscriptionb2caddon";
	}
}
