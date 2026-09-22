package com.adyen.service;

import de.hybris.platform.cissapdigitalpayment.model.SAPDigitalPaymentConfigurationModel;
import de.hybris.platform.cissapdigitalpayment.service.CisSapDigitalPaymentService;

import jakarta.ws.rs.PathParam;

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

import rx.Observable;


public interface AdyenSapDigitalPaymentService extends CisSapDigitalPaymentService {
	/**
	 * Requests for initiate credit card into the SAP Digital Payment Addon
	 */
	DigitalPaymentsCardResultList getForPaymentCard(final DigitalGetPaymentCardList paymentCardRequestList,
	                                                final SAPDigitalPaymentConfigurationModel
			                                                sapDigitalPaymentConfig);

	/**
	 * Requests for initiate card authorization into the SAP Digital Payment Addon
	 */
	DigitalPaymentGetAuthorizationResultList getForPaymentCardAuthorization(
			final DigitalPaymentGetAuthorizationList authorizationList,
			final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig);

	/**
	 * Requests for initiate card capture into the SAP Digital Payment Addon
	 */
	DigitalPaymentGetCaptureResultList getForDirectCapture(final DigitalPaymentGetCaptureList captureList,
	                                                       final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig);

	/**
	 * Requests for initiate card with authorization into the SAP Digital Payment Addon
	 */
	DigitalPaymentGetCardWithAuthorizationResultModelList getForPaymentCardWithAuthorizationForDPA(
			final DigitalPaymentGetCardWithAuthorizationList authorizationList,
			SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig);

	/**
	 * Requests for initiate an authorization into the SAP Digital Payment Addon
	 *
	 * @param paymentCardRequestList
	 * @return
	 */
	DigitalPaymentGetAuthorizationResultList getForPaymentAuthorization(
			final DigitalPaymentGetAuthorizationList paymentCardRequestList,
			final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig);

	/**
	 * Requests for initiate authorizations into the SAP Digital Payment Addon
	 */
	DigitalPaymentGetAuthorizationResultList getAuthorization(final FetchAuthorizationList authorizationList,
	                                                          final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig);

	Observable<PaymentCardResult> getPaymentCardDetails(@PathParam("token") final String dpaToken, final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfig);

}
