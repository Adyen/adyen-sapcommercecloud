package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import com.adyen.sapdigitalpaymentbackoffice.utils.DPAPaymentDisplayFormatter;
import com.adyen.service.model.DPACaptureResult;
import com.adyen.service.DPAPaymentOrchestrationService;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import java.util.List;
import java.util.Optional;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;
import org.springframework.beans.factory.annotation.Autowired;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.CAPTURE_REGISTERED_SUCCESSFULLY_IN_DPA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.NO_CAPTURE_TRANSACTION;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RESULT_MODEL_IS_EMPTY;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showError;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showMessageBox;

public class RegisterCaptureWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	@Autowired
	private DPAPaymentOrchestrationService dpaPaymentOrchestrationService;

	@Autowired
	private DPAPaymentDisplayFormatter dpaPaymentDisplayFormatter;

	@Override
	public ActionResult<Object> perform(final ActionContext<OrderModel> actionContext) {
		final OrderModel orderModel = actionContext.getData();

		if (getCapturePayment(orderModel.getPaymentTransactions()).isEmpty()) {
			return showError(NO_CAPTURE_TRANSACTION);
		}

		final DPACaptureResult result = dpaPaymentOrchestrationService.registerCapture(orderModel);

		if (!result.hasResult()) {
			return showError(RESULT_MODEL_IS_EMPTY);
		}

		if (!result.isSuccess()) {
			return showError(result.getResultDesc());
		}

		final String displayData =
				dpaPaymentDisplayFormatter.buildCaptureDisplay(result.getResultModel());

		showMessageBox(displayData, CAPTURE_REGISTERED_SUCCESSFULLY_IN_DPA);
		return new ActionResult<>(ActionResult.SUCCESS, result.getRawResultList());
	}

	@Override
	public boolean canPerform(final ActionContext<OrderModel> ctx) {
		final OrderModel orderModel = ctx.getData();
		return orderModel != null && getCapturePayment(orderModel.getPaymentTransactions()).isPresent();
	}

	private Optional<PaymentTransactionEntryModel> getCapturePayment(List<PaymentTransactionModel> paymentTransactions) {
		return paymentTransactions.stream()
				.flatMap(tx -> tx.getEntries().stream())
				.filter(entry -> PaymentTransactionType.CAPTURE.equals(entry.getType()))
				.findFirst();
	}
}
