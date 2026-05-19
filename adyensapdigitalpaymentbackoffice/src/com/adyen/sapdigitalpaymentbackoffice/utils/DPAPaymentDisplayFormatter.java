package com.adyen.sapdigitalpaymentbackoffice.utils;

import com.adyen.model.DigitalPaymentsCardResultModel;
import com.adyen.model.DigitalPaymentGetCaptureResultModel;
import com.adyen.model.DigitalPaymentGetAuthorizationResultList;
import com.adyen.model.DigitalPaymentGetAuthorizationResult;

import static com.adyen.service.utils.ActionConstants.*;

public class DPAPaymentDisplayFormatter {

	public String buildCardDisplay(final DigitalPaymentsCardResultModel result) {
		var tx = result.getDigitalPaymentTransaction();

		return CARD_INFO_TEMPLATE.formatted(
				result.getPaymentServiceProvider(),
				tx != null ? tx.getDigitalPaymentTransaction() : "n/a",
				tx != null ? tx.getDigitalPaytTransResult() : "n/a",
				tx != null ? tx.getDigitalPaytTransRsltDesc() : "n/a",
				result.getPaytCardByPaytServiceProvider()
		);
	}

	public String buildCaptureDisplay(final DigitalPaymentGetCaptureResultModel result) {
		var tx = result.getDigitalPaymentTransaction();

		return CAPTURE_INFO_TEMPLATE.formatted(
				result.getMerchantAccount(),
				result.getPaymentServiceProvider(),
				result.getPaymentByPaymentServicePrvdr(),
				tx != null ? tx.getDigitalPaymentTransaction() : "n/a",
				tx != null ? tx.getDigitalPaytTransResult() : "n/a",
				tx != null ? tx.getDigitalPaytTransRsltDesc() : "n/a"
		);
	}

	public String buildAuthorizationDisplay(final DigitalPaymentGetAuthorizationResultList results) {
		if (results == null || results.getAuthorizationResults() == null
				|| results.getAuthorizationResults().isEmpty()) {
			return "No authorization details.";
		}

		final StringBuilder sb = new StringBuilder();

		for (DigitalPaymentGetAuthorizationResult result : results.getAuthorizationResults()) {
			var auth = result.getAuthorization();
			var tx = result.getDigitalPaymentTransaction();

			sb.append(AUTH_INFO_TEMPLATE.formatted(
					auth.getAuthorizedAmountInAuthznCrcy(),
					auth.getAuthorizationCurrency(),
					auth.getAuthorizationDateTime(),
					auth.getAuthorizationExpirationDateTme(),
					auth.getAuthorizationByDigitalPaytSrvc(),
					auth.getAuthorizationByPaytSrvcPrvdr(),
					auth.getDetailedAuthorizationStatus(),
					tx != null ? tx.getDigitalPaymentTransaction() : "n/a",
					tx != null ? tx.getDigitalPaytTransResult() : "n/a",
					tx != null ? tx.getDigitalPaytTransRsltDesc() : "n/a"
			));

			if (result.getSource() != null && result.getSource().getCard() != null) {
				sb.append("\n Card DPA Token: ")
						.append(result.getSource().getCard().getPaytCardByDigitalPaymentSrvc())
						.append("\n");
			}
			if (result.getSource() != null && result.getSource().getMerchant() != null) {
				sb.append(" Merchant Account: ")
						.append(result.getSource().getMerchant().getAccount())
						.append("\n");
			}

			sb.append("\n");
		}

		return sb.toString();
	}
}
