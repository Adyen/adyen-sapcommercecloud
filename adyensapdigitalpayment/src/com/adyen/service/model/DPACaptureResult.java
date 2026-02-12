package com.adyen.service.model;

import com.adyen.model.DigitalPaymentGetCaptureResultList;
import com.adyen.model.DigitalPaymentGetCaptureResultModel;

public class DPACaptureResult extends DPAResult<
		DigitalPaymentGetCaptureResultModel,
		DigitalPaymentGetCaptureResultList
		> {

	public DPACaptureResult(
			boolean hasResult,
			boolean success,
			String resultCode,
			String resultDesc,
			DigitalPaymentGetCaptureResultModel result,
			DigitalPaymentGetCaptureResultList rawResultList
	) {
		super(hasResult, success, resultCode, resultDesc, result, rawResultList);
	}

	public static DPACaptureResult empty() {
		return new DPACaptureResult(false, false, null, null, null, null);
	}
}
