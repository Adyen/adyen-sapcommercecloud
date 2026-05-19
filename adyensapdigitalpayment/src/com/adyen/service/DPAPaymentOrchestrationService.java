package com.adyen.service;

import com.adyen.service.model.DPACardResult;
import com.adyen.service.model.DPACaptureResult;
import com.adyen.service.model.DPAAuthorizationResult;
import de.hybris.platform.core.model.order.OrderModel;

/**
 * Orchestrates payment-related operations in SAP Digital Payments Add-on (DPA)
 * for Adyen-based payment flows.
 *
 * <p>
 * The service acts as a coordination layer between SAP Commerce orders,
 * payment transactions and the SAP Digital Payments Add-on APIs.
 * </p>
 */
public interface DPAPaymentOrchestrationService {

	/**
	 * Registers a capture in SAP Digital Payments Add-on (DPA) for the given order.
	 * <p>
	 * The implementation is expected to locate a CAPTURE payment transaction
	 * on the order, call DPA to register the capture and persist the resulting
	 * DPA operation details.import com.adyen.model.authorization.FetchAuthorization;
	 * </p>
	 **/
	DPACaptureResult registerCapture(OrderModel order);

	/**
	 * Retrieves authorization details from DPA for the given order.
	 * <p>
	 * The authorization is retrieved using the DPA authorization code stored
	 * on the order payment information. No changes to the order are expected,
	 * except persisting DPA operation logs.
	 * </p>
	 **/
	DPAAuthorizationResult retrieveAuthorization(OrderModel order);

	/**
	 * Registers a payment card token in DPA for the given order.
	 * <p>
	 * The implementation is expected to register the shopper's payment card
	 * in DPA and update the order payment information with the returned token.
	 * </p>
	 **/
	DPACardResult registerCard(OrderModel order);

	/**
	 * Registers an authorization in DPA for the given order.
	 * <p>
	 * The implementation is expected to locate an AUTHORIZATION payment transaction
	 * on the order, register it in DPA and store the returned authorization code
	 * on the order payment information.
	 * </p>
	 **/
	DPAAuthorizationResult registerAuthorization(OrderModel order);
}
