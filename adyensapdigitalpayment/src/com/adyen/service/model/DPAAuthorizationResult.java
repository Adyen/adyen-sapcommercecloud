package com.adyen.service.model;

import com.adyen.model.DigitalPaymentGetAuthorizationResult;
import com.adyen.model.DigitalPaymentGetAuthorizationResultList;

public class DPAAuthorizationResult extends DPAResult<
		DigitalPaymentGetAuthorizationResult,
		DigitalPaymentGetAuthorizationResultList
		> {

	public DPAAuthorizationResult(
			boolean hasResult,
			boolean success,
			String resultCode,
			String resultDesc,
			DigitalPaymentGetAuthorizationResult result,
			DigitalPaymentGetAuthorizationResultList rawResultList
	) {
		super(hasResult, success, resultCode, resultDesc, result, rawResultList);
	}

	public static DPAAuthorizationResult empty() {
		return new DPAAuthorizationResult(false, false, null, null, null, null);
	}
}
