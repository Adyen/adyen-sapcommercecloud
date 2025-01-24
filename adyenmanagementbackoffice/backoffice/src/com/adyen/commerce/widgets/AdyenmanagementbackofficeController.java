/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved
 */
package com.adyen.commerce.widgets;

import com.hybris.cockpitng.annotations.ViewEvent;
import org.apache.log4j.Logger;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;

import com.hybris.cockpitng.util.DefaultWidgetController;

import org.zkoss.zul.Textbox;


public class AdyenmanagementbackofficeController extends DefaultWidgetController
{
	Logger LOG = Logger.getLogger(AdyenmanagementbackofficeController.class);
	private static final long serialVersionUID = 1L;

	private Textbox request;
	private Textbox response;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
	}

	@ViewEvent(componentID = "adyen_execute", eventName = Events.ON_CLICK)
	public void onClick() {
		LOG.info(request.getValue());
		response.setValue("test");
	}

}
