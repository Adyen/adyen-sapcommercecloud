/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved
 */
package com.adyen.sapdigitalpaymentbackoffice.widgets;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Label;

import com.hybris.cockpitng.util.DefaultWidgetController;

import com.adyen.sapdigitalpaymentbackoffice.services.AdyendigitalpaymentbackofficeService;


public class AdyendigitalpaymentbackofficeController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;
	private Label label;

	@WireVariable
	private transient AdyendigitalpaymentbackofficeService adyendigitalpaymentbackofficeService;

	@Override
	public void initialize(final Component comp)
	{
		super.initialize(comp);
		label.setValue(adyendigitalpaymentbackofficeService.getHello() + " AdyendigitalpaymentbackofficeController");
	}
}
