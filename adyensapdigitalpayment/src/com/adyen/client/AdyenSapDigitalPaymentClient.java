package com.adyen.client;

import com.adyen.model.*;
import com.adyen.model.authorization.*;
import com.hybris.charon.annotations.*;
import de.hybris.platform.cissapdigitalpayment.client.*;
import rx.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

/*
 *Client to connect to SAP Digital Payment Addon using Charon API.
 */
@OAuth
public interface AdyenSapDigitalPaymentClient extends SapDigitalPaymentClient {
	/*
	 * Check payment card and provide SAP digital payments add-on token for it.
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/tokens/getforpaymentcard")
	@Control(retries = "${retries:3}", retriesInterval = "${retriesInterval:2000}", timeout = "${timeout:4000}")
	DigitalPaymentsCardResultList getForPaymentCard(final DigitalGetPaymentCardList paymentCardRequestList);

	/*
	 *Check authorization for payment card and provide SAP digital payments add-on token for it.
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/tokens/getforpaymentcardauthorization")
	@Control(retries = "${retries:3}", retriesInterval = "${retriesInterval:2000}", timeout = "${timeout:4000}")
	DigitalPaymentGetAuthorizationResultList getForPaymentCardAuthorization(
			final DigitalPaymentGetAuthorizationList paymentCardRequestList);

	/*
	 *Check direct capture for external payment and provide SAP digital payments add-on token for it.
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/tokens/getfordirectcapture")
	@Control(retries = "${retries:3}", retriesInterval = "${retriesInterval:2000}", timeout = "${timeout:4000}")
	DigitalPaymentGetCaptureResultList getForDirectCapture(final DigitalPaymentGetCaptureList paymentGetCaptureList);

	/*
	 *Check payment card as well as authorization and provide SAP digital payments add-on tokens for both.
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/tokens/getforpaymentcardwithauthorization")
	@Control(retries = "${retries:3}", retriesInterval = "${retriesInterval:2000}", timeout = "${timeout:4000}")
	DigitalPaymentGetCardWithAuthorizationResultModelList getForPaymentCardWithAuthorization(
			final DigitalPaymentGetCardWithAuthorizationList paymentCardAuthorizationRequestList);

	/*
	 *Check authorization for external payment and provide SAP digital payments add-on token for it.
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/tokens/getforauthorization")
	@Control(retries = "${retries:3}", retriesInterval = "${retriesInterval:2000}", timeout = "${timeout:4000}")
	DigitalPaymentGetAuthorizationResultList getForAuthorization(
			final DigitalPaymentGetAuthorizationList paymentCardRequestList);

	/*
	 *Get card data registered with token created by SAP digital payments add-on.
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/cards/{token}")
	@Control(retries = "${retries:3}", retriesInterval = "${retriesInterval:2000}", timeout = "${timeout:4000}")
	Observable<PaymentCardResult> getPaymentCardDetails(@PathParam("token") final String dpaToken);

	/*
	 *Get authorizations based on SAP digital payments add-on authorization identifiers.
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/authorizations/getbydigitalpaytsrvc")
	@Control(retries = "${retries:3}", retriesInterval = "${retriesInterval:2000}", timeout = "${timeout:4000}")
	DigitalPaymentGetAuthorizationResultList getAuthorization(final FetchAuthorizationList authorizationList);
}
