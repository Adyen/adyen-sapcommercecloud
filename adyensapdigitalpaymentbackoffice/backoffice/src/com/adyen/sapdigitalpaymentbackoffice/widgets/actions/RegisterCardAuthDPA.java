package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.AUTHORIZATION_TRANSACTION_NOT_FOUND;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.CARD_AUTHORIZATION_REGISTERED_SUCCESSFULLY_IN_DPA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showError;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showMessageBox;
import com.adyen.sapdigitalpaymentbackoffice.utils.DPAPaymentDisplayFormatter;
import com.adyen.service.model.DPAAuthorizationResult;
import com.adyen.service.DPAPaymentOrchestrationService;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;
import org.springframework.beans.factory.annotation.Autowired;

public class RegisterCardAuthDPA extends AbstractComponentWidgetAdapterAware
		implements CockpitAction<OrderModel, Object> {

	@Autowired
	private DPAPaymentOrchestrationService dpaPaymentOrchestrationService;

	@Autowired
	private DPAPaymentDisplayFormatter displayFormatter;

	@Override
	public ActionResult<Object> perform(ActionContext<OrderModel> ctx) {
		final OrderModel order = ctx.getData();

		final DPAAuthorizationResult result =
				dpaPaymentOrchestrationService.registerAuthorization(order);

		if (!result.hasResult()) {
			return showError(AUTHORIZATION_TRANSACTION_NOT_FOUND);
		}

		if (!result.isSuccess()) {
			return showError(result.getResultDesc());
		}

		final String display =
				displayFormatter.buildAuthorizationDisplay(result.getRawResultList());

		showMessageBox(display, CARD_AUTHORIZATION_REGISTERED_SUCCESSFULLY_IN_DPA);
		return new ActionResult<>(ActionResult.SUCCESS, result.getRawResultList());
	}

	@Override
	public boolean canPerform(ActionContext<OrderModel> ctx) {
		final OrderModel order = ctx.getData();
		return order != null
				&& order.getPaymentTransactions() != null
				&& order.getPaymentTransactions().stream()
				.flatMap(tx -> tx.getEntries().stream())
				.anyMatch(e -> PaymentTransactionType.AUTHORIZATION.equals(e.getType()));
	}
}
