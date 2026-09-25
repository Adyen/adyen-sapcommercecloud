package com.adyen.cockpit.widgets;

import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Iframe;

import com.hybris.cockpitng.util.DefaultWidgetController;

/**
 * Carries the cockpit application and nothing else; everything the merchant sees is served from the
 * web fragment inside the Backoffice webapp.
 */
public class AdyenCockpitController extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;

	private static final String FRAME_HOLDER = "cockpitFrameHolder";

	/**
	 * Relative to the Backoffice context: ZK prepends the context path to a source starting with "/", so
	 * adding it here as well produces /backoffice/backoffice/... . The page is named rather than left to
	 * the welcome files, because Backoffice lists cockpit.zul first and a directory request would reach ZK.
	 */
	private static final String APP_PAGE = "/adyencockpit/index.html";

	@Override
	public void initialize(final Component component)
	{
		super.initialize(component);

		final Iframe frame = new Iframe();
		frame.setWidth("100%");
		frame.setHeight("100%");
		frame.setStyle("border: none;");
		// Same webapp as Backoffice, so the frame carries the session the user already has.
		frame.setSrc(APP_PAGE);

		component.getFellow(FRAME_HOLDER).appendChild(frame);
	}
}
