package com.adyen.sapdigitalpaymentbackoffice.jalo;

import com.adyen.sapdigitalpaymentbackoffice.constants.AdyensapdigitalpaymentbackofficeConstants;
import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.extension.ExtensionManager;
import org.apache.log4j.Logger;

public class AdyensapdigitalpaymentbackofficeManager extends GeneratedAdyensapdigitalpaymentbackofficeManager
{
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger( AdyensapdigitalpaymentbackofficeManager.class.getName() );
	
	public static final AdyensapdigitalpaymentbackofficeManager getInstance()
	{
		ExtensionManager em = JaloSession.getCurrentSession().getExtensionManager();
		return (AdyensapdigitalpaymentbackofficeManager) em.getExtension(AdyensapdigitalpaymentbackofficeConstants.EXTENSIONNAME);
	}
	
}
