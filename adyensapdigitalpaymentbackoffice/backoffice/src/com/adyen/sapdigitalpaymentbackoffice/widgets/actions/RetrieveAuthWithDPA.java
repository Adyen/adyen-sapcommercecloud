package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.AUTHORIZATION_DOESN_T_EXIST;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RECEIVED_AUTHORIZATION_DETAILS;

import com.adyen.sapdigitalpaymentbackoffice.utils.DPAPaymentDisplayFormatter;
import com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil;
import com.adyen.service.model.DPAAuthorizationResult;
import com.adyen.service.DPAPaymentOrchestrationService;
import de.hybris.platform.core.model.order.OrderModel;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;
import org.springframework.beans.factory.annotation.Autowired;

public class RetrieveAuthWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	@Autowired
	private DPAPaymentOrchestrationService dpaPaymentOrchestrationService;

	@Autowired
	private DPAPaymentDisplayFormatter dpaPaymentDisplayFormatter;

	@Override
	public ActionResult<Object> perform(final ActionContext<OrderModel> context) {
		final OrderModel orderModel = context.getData();

		final DPAAuthorizationResult result =
				dpaPaymentOrchestrationService.retrieveAuthorization(orderModel);

		if (!result.hasResult()) {
			return MessageBoxUtil.showError(AUTHORIZATION_DOESN_T_EXIST);
		}

		final String displayData =
				dpaPaymentDisplayFormatter.buildAuthorizationDisplay(result.getRawResultList());

		return MessageBoxUtil.showSuccess(displayData, RECEIVED_AUTHORIZATION_DETAILS);
	}

	@Override
	public boolean canPerform(final ActionContext<OrderModel> context) {
		final OrderModel orderModel = context.getData();
		return orderModel != null
				&& orderModel.getPaymentInfo() != null
				&& orderModel.getPaymentInfo().getAdyenDPAAuthorizationCode() != null;
	}
}
