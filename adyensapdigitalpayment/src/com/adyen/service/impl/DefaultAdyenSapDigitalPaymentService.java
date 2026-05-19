package com.adyen.service.impl;

import static de.hybris.platform.cissapdigitalpayment.constants.CisSapDigitalPaymentConstant.SAP_DIGITAL_PAYMENT_PAYMENT_METHOD_KEY;
import de.hybris.platform.cissapdigitalpayment.client.model.CisSapDigitalPaymentPollRegisteredCardResult;
import de.hybris.platform.cissapdigitalpayment.model.SAPDigitalPaymentConfigurationModel;
import de.hybris.platform.cissapdigitalpayment.service.impl.DefaultCisSapDigitalPaymentService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.adyen.client.AdyenSapDigitalPaymentClient;
import com.adyen.model.DigitalGetPaymentCardList;
import com.adyen.model.DigitalPaymentGetAuthorizationList;
import com.adyen.model.DigitalPaymentGetAuthorizationResultList;
import com.adyen.model.DigitalPaymentGetCaptureList;
import com.adyen.model.DigitalPaymentGetCaptureResultList;
import com.adyen.model.DigitalPaymentGetCardWithAuthorizationList;
import com.adyen.model.DigitalPaymentGetCardWithAuthorizationResultModelList;
import com.adyen.model.DigitalPaymentsCardResultList;
import com.adyen.model.PaymentCardResult;
import com.adyen.model.authorization.FetchAuthorizationList;
import com.adyen.service.AdyenSapDigitalPaymentService;
import com.hybris.charon.Charon;
import rx.Observable;

public class DefaultAdyenSapDigitalPaymentService extends DefaultCisSapDigitalPaymentService implements AdyenSapDigitalPaymentService {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenSapDigitalPaymentService.class);

	private static void logSuccess(final String message) {
		LOG.info(message);
	}

	private static void logError(final Throwable error) {
		LOG.error("Error while fetching the response " + error);
	}

	@Override
	public DigitalPaymentsCardResultList getForPaymentCard(final DigitalGetPaymentCardList paymentCardRequestList,
	                                                       final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		return getAdyenCisSapDigitalPaymentClient(sapDigitalPaymentConfig).getForPaymentCard(paymentCardRequestList);
	}

	@Override
	public DigitalPaymentGetAuthorizationResultList getForPaymentCardAuthorization(
			final DigitalPaymentGetAuthorizationList authorizationList,
			final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		return getAdyenCisSapDigitalPaymentClient(sapDigitalPaymentConfig).getForPaymentCardAuthorization(authorizationList);
	}

	@Override
	public DigitalPaymentGetCaptureResultList getForDirectCapture(final DigitalPaymentGetCaptureList captureList,
	                                                              final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		return getAdyenCisSapDigitalPaymentClient(sapDigitalPaymentConfig).getForDirectCapture(captureList);
	}

	@Override
	public DigitalPaymentGetCardWithAuthorizationResultModelList getForPaymentCardWithAuthorizationForDPA(
			final DigitalPaymentGetCardWithAuthorizationList authorizationList,
			final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		return getAdyenCisSapDigitalPaymentClient(sapDigitalPaymentConfig).getForPaymentCardWithAuthorization(authorizationList);
	}

	public AdyenSapDigitalPaymentClient getAdyenCisSapDigitalPaymentClient(
			final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {

		return Charon.from(AdyenSapDigitalPaymentClient.class).config(createDigitalPaymentConfigurationMap(sapDigitalPaymentConfig))
				.build();
	}


	public DigitalPaymentGetAuthorizationResultList getForPaymentAuthorization(
			final DigitalPaymentGetAuthorizationList paymentCardRequestList,
			final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		return getAdyenCisSapDigitalPaymentClient(sapDigitalPaymentConfig).getForAuthorization(paymentCardRequestList);
	}


	@Override
	public Observable<CisSapDigitalPaymentPollRegisteredCardResult> pollRegisteredCard(final String sessionId,
	                                                                                   final SAPDigitalPaymentConfigurationModel sapDigiPayConfig) {
		return getCisSapDigitalPaymentClient(sapDigiPayConfig).pollRegisteredCard(sessionId);
	}

	@Override
	protected Map<String, String> createDigitalPaymentConfigurationMap(SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		Map<String, String> ret = super.createDigitalPaymentConfigurationMap(sapDigitalPaymentConfig);
		ret.put(SAP_DIGITAL_PAYMENT_PAYMENT_METHOD_KEY,
				sapDigitalPaymentConfig.getPaymentMethod());

		return ret;
	}

	public DigitalPaymentGetAuthorizationResultList getAuthorization(final FetchAuthorizationList authorizationList,
	                                                                 final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		return getAdyenCisSapDigitalPaymentClient(sapDigitalPaymentConfig).getAuthorization(authorizationList);
	}

	@Override
	public Observable<PaymentCardResult> getPaymentCardDetails(final String dpaToken,
	                                                           final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig) {
		return getAdyenCisSapDigitalPaymentClient(sapDigitalPaymentConfig).getPaymentCardDetails(dpaToken).map(card ->
		{
			logSuccess("Successfully poll the registered card");
			return card;
		}).doOnError(DefaultAdyenSapDigitalPaymentService::logError).onErrorReturn(throwable -> null);
	}
}
