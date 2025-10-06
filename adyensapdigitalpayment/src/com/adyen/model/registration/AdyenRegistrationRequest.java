package com.adyen.model.registration;

import com.fasterxml.jackson.annotation.*;
import de.hybris.platform.cissapdigitalpayment.client.model.*;

public class AdyenRegistrationRequest extends DigitalPaymentsRegistrationRequest {

	@JsonProperty("PaytCardRegnLifeCycleType")
	private String paytCardRegnLifeCycleType;

	public String getPaytCardRegnLifeCycleType() {
		return paytCardRegnLifeCycleType;
	}

	public void setPaytCardRegnLifeCycleType(String paytCardRegnLifeCycleType) {
		this.paytCardRegnLifeCycleType = paytCardRegnLifeCycleType;
	}
}
