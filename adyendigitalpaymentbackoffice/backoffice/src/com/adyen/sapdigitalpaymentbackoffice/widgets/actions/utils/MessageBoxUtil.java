package com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils;

import com.hybris.cockpitng.actions.*;
import org.zkoss.zul.*;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.ERROR_DETAILS;

public class MessageBoxUtil {

	public static void showMessageBox(String message, String title) {
		Messagebox.show(message, title,
				new Messagebox.Button[]{Messagebox.Button.OK, Messagebox.Button.CANCEL}, null, null, null);
	}

	public static ActionResult showSuccess(String message, String title) {
		Messagebox.show(message, title, new Messagebox.Button[]
				{Messagebox.Button.OK, Messagebox.Button.CANCEL}, null, null, null);
		return new ActionResult(ActionResult.SUCCESS);
	}

	public static ActionResult<Object> showError(String message) {
		showMessageBox(message, ERROR_DETAILS);
		return new ActionResult<>(ActionResult.ERROR);
	}

	private MessageBoxUtil() {

	}
}