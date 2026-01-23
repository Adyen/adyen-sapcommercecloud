package com.adyen.v6.actions;

import com.adyen.service.DPAPaymentOrchestrationService;
import com.adyen.service.DPAAuthorizationResult;
import com.adyen.service.DPACardResult;
import com.adyen.service.DPACaptureResult;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.processengine.action.AbstractSimpleDecisionAction;
import de.hybris.platform.task.RetryLaterException;
import org.apache.log4j.Logger;

public class AdyenCheckCardAndDirectCaptureAction extends AbstractSimpleDecisionAction<OrderProcessModel> {

	private static final Logger LOG = Logger.getLogger(AdyenCheckCardAndDirectCaptureAction.class);

	private final DPAPaymentOrchestrationService dpaPaymentOrchestrationService;

	public AdyenCheckCardAndDirectCaptureAction(final DPAPaymentOrchestrationService dpaPaymentOrchestrationService) {
		this.dpaPaymentOrchestrationService = dpaPaymentOrchestrationService;
	}

	@Override
	public Transition executeAction(final OrderProcessModel process) throws RetryLaterException {
		final OrderModel order = process.getOrder();

		if (order == null) {
			LOG.error("Order is missing for process " + process.getCode());
			return Transition.NOK;
		}

		LOG.info("Starting DPA flow for order " + order.getCode());

		LOG.info("[1/3] Registering card...");
		final DPACardResult card = dpaPaymentOrchestrationService.registerCard(order);

		if (!card.hasResult()) {
			LOG.warn("[1/3] No card result returned.");
			return Transition.NOK;
		}
		if (!card.isSuccess()) {
			LOG.error("[1/3] Card registration failed: " + card.getResultDesc());
			return Transition.NOK;
		}
		LOG.info("[1/3] Card successfully registered.");

		LOG.info("[2/3] Registering authorization...");
		final DPAAuthorizationResult auth = dpaPaymentOrchestrationService.registerAuthorization(order);

		if (!auth.hasResult()) {
			LOG.warn("[2/3] No authorization result returned.");
			return Transition.NOK;
		}
		if (!auth.isSuccess()) {
			LOG.error("[2/3] Registering authorization failed: " + auth.getResultDesc());
			return Transition.NOK;
		}
		LOG.info("[2/3] Registered Authorization successfully.");

		LOG.info("[3/3] Registering capture...");
		final DPACaptureResult cap = dpaPaymentOrchestrationService.registerCapture(order);

		if (!cap.hasResult()) {
			LOG.warn("[3/3] No capture result returned.");
			return Transition.NOK;
		}
		if (!cap.isSuccess()) {
			LOG.error("[3/3] Capture failed: " + cap.getResultDesc());
			return Transition.NOK;
		}
		LOG.info("[3/3] Capture registered successfully.");

		LOG.info("DPA flow completed successfully for order " + order.getCode());
		return Transition.OK;
	}
}