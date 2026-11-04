package com.adyen.v6.jalo;

import de.hybris.platform.jalo.Item;
import de.hybris.platform.jalo.JaloBusinessException;
import de.hybris.platform.jalo.SessionContext;
import de.hybris.platform.jalo.type.ComposedType;
import org.apache.log4j.Logger;

public class AdyenDonation extends GeneratedAdyenDonation
{
	@SuppressWarnings("unused")
	private static final Logger LOG = Logger.getLogger( AdyenDonation.class.getName() );
	
	@Override
	protected Item createItem(final SessionContext ctx, final ComposedType type, final ItemAttributeMap allAttributes) throws JaloBusinessException
	{
		return super.createItem( ctx, type, allAttributes );
	}
	
}
