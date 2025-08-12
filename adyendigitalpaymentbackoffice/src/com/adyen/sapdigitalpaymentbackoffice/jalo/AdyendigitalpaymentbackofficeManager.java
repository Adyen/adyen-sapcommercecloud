package com.adyen.sapdigitalpaymentbackoffice.jalo;

import com.adyen.sapdigitalpaymentbackoffice.constants.AdyendigitalpaymentbackofficeConstants;
import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.extension.ExtensionManager;
import org.apache.log4j.Logger;

public class AdyendigitalpaymentbackofficeManager extends GeneratedAdyendigitalpaymentbackofficeManager
{
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger( AdyendigitalpaymentbackofficeManager.class.getName() );
	
	public static final AdyendigitalpaymentbackofficeManager getInstance()
	{
		ExtensionManager em = JaloSession.getCurrentSession().getExtensionManager();
		return (AdyendigitalpaymentbackofficeManager) em.getExtension(AdyendigitalpaymentbackofficeConstants.EXTENSIONNAME);
	}
	
}
