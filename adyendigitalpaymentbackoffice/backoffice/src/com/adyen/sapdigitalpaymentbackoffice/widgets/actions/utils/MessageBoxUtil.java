package com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils;

import org.zkoss.zul.*;

public class MessageBoxUtil {

	public static void showMessageBox(String message, String title) {
		Messagebox.show(message, title,
				new Messagebox.Button[]{Messagebox.Button.OK, Messagebox.Button.CANCEL}, null, null, null);
	}
	private MessageBoxUtil(){

	}
}

