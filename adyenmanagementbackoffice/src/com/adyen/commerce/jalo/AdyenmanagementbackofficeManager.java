package com.adyen.commerce.jalo;

import com.adyen.commerce.constants.AdyenmanagementbackofficeConstants;
import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.extension.ExtensionManager;
import org.apache.log4j.Logger;

public class AdyenmanagementbackofficeManager extends GeneratedAdyenmanagementbackofficeManager
{
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger( AdyenmanagementbackofficeManager.class.getName() );
	
	public static final AdyenmanagementbackofficeManager getInstance()
	{
		ExtensionManager em = JaloSession.getCurrentSession().getExtensionManager();
		return (AdyenmanagementbackofficeManager) em.getExtension(AdyenmanagementbackofficeConstants.EXTENSIONNAME);
	}
	
}
