package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.CARD_REGISTERED_SUCCESSFULLY_IN_DPA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RESULT_MODEL_IS_EMPTY;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showError;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showMessageBox;

import com.adyen.sapdigitalpaymentbackoffice.utils.DPAPaymentDisplayFormatter;
import com.adyen.service.model.DPACardResult;
import com.adyen.service.DPAPaymentOrchestrationService;
import de.hybris.platform.core.model.order.OrderModel;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;
import org.springframework.beans.factory.annotation.Autowired;

public class RegisterCardWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	@Autowired
	private DPAPaymentOrchestrationService dpaPaymentOrchestrationService;

	@Autowired
	private DPAPaymentDisplayFormatter dpaPaymentDisplayFormatter;

	@Override
	public ActionResult<Object> perform(ActionContext<OrderModel> actionContext) {
		final OrderModel orderModel = actionContext.getData();


		final DPACardResult result = dpaPaymentOrchestrationService.registerCard(orderModel);

		if (!result.hasResult()) {
			return showError(RESULT_MODEL_IS_EMPTY);
		}

		if (!result.isSuccess()) {
			return showError(result.getResultDesc());
		}

		final String displayData =
				dpaPaymentDisplayFormatter.buildCardDisplay(result.getResultModel());

		showMessageBox(displayData, CARD_REGISTERED_SUCCESSFULLY_IN_DPA);
		return new ActionResult<>(ActionResult.SUCCESS, result.getRawResultList());
	}

	@Override
	public boolean canPerform(ActionContext<OrderModel> ctx) {
		return ctx.getData() != null;
	}
}