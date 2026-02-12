package com.adyen.service.model;

import com.adyen.model.DigitalPaymentsCardResultList;
import com.adyen.model.DigitalPaymentsCardResultModel;

public class DPACardResult extends DPAResult<
		DigitalPaymentsCardResultModel,
		DigitalPaymentsCardResultList
		> {

	public DPACardResult(
			boolean hasResult,
			boolean success,
			String resultCode,
			String resultDesc,
			DigitalPaymentsCardResultModel result,
			DigitalPaymentsCardResultList rawResultList
	) {
		super(hasResult, success, resultCode, resultDesc, result, rawResultList);
	}

	public static DPACardResult empty() {
		return new DPACardResult(false, false, null, null, null, null);
	}
}

