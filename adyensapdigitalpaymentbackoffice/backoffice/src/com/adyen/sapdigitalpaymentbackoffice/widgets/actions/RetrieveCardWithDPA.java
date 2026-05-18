package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.CARD_DOESN_T_EXIST;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RETRIEVE_CARD_WITH_DPA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RETRIVED_CARD_DETAILS;

import de.hybris.platform.cissapdigitalpayment.model.SAPDigitalPaymentConfigurationModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.model.ModelService;

import java.time.Instant;
import java.util.Date;
import jakarta.annotation.Resource;

import org.zkoss.zk.ui.Component;

import com.adyen.model.DPAOperationResultModel;
import com.adyen.model.PaymentCardResult;
import com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil;
import com.adyen.service.AdyenSapDigitalPaymentService;
import com.google.gson.Gson;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;
import com.hybris.cockpitng.util.WidgetUtils;

public class RetrieveCardWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	private static final String CARD_INFORMATION = """
			Received following card information from DPA:
			Card Holder: %s
			Card DPA Token: %s
			Card Type:  %s
			Card Mask Number:  %s
			Exp Year:  %s
			Exp Month:  %s
			""";

	@Resource
	private WidgetUtils widgetUtils;

	@Resource
	private AdyenSapDigitalPaymentService adyenSapDigitalPaymentService;

	@Resource
	private ModelService modelService;

	@Override
	public boolean canPerform(final ActionContext<OrderModel> context) {
		boolean ret = false;
		final OrderModel orderModel = context.getData();
		ret = orderModel.getPaymentInfo().getAdyenDPAPaymentCardToken() != null;
		return ret;
	}

	@Override
	public ActionResult<Object> perform(final ActionContext<OrderModel> context) {

		final OrderModel orderModel = context.getData();
		final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfiguration = orderModel.getStore().getSapDigitalPaymentConfiguration();
		final PaymentCardResult paymentCardResult = adyenSapDigitalPaymentService.getPaymentCardDetails(orderModel.getPaymentInfo().getAdyenDPAPaymentCardToken(), sapDigitalPaymentConfiguration).toBlocking().first();
		final StringBuilder dataToDisplay = new StringBuilder();
		final StringBuilder dataToSaveToModel = new StringBuilder();

		if (paymentCardResult != null) {
			dataToSaveToModel.append("Raw data:").append(new Gson().toJson(paymentCardResult));
			final DPAOperationResultModel dpaOperationResult = buildDPAOperationResultModel(dataToSaveToModel, paymentCardResult);
			modelService.save(dpaOperationResult);

			final String formatted = prepareCardInfo(paymentCardResult);
			dataToDisplay.append(formatted);
		} else {
			dataToDisplay.append(CARD_DOESN_T_EXIST);
		}
		return MessageBoxUtil.showSuccess(dataToDisplay.toString(),RETRIVED_CARD_DETAILS);
	}

	private static DPAOperationResultModel buildDPAOperationResultModel(final StringBuilder dataToSaveToModel, final PaymentCardResult paymentCardResult) {
		final DPAOperationResultModel dpaOperationResult = new DPAOperationResultModel();
		dpaOperationResult.setDpaOperationPayload(dataToSaveToModel.toString());
		dpaOperationResult.setDpaOperation(RETRIEVE_CARD_WITH_DPA);
		dpaOperationResult.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResult.setDpaCardReference(paymentCardResult.getPaytCardByDigitalPaymentSrvc());
		return dpaOperationResult;
	}

	private static String prepareCardInfo(PaymentCardResult paymentCardResult) {
		return CARD_INFORMATION.formatted(
				String.valueOf(paymentCardResult.getPaymentCardHolderName()),
				String.valueOf(paymentCardResult.getPaytCardByDigitalPaymentSrvc()),
				String.valueOf(paymentCardResult.getPaymentCardType()),
				String.valueOf(paymentCardResult.getPaymentCardMaskedNumber()),
				String.valueOf(paymentCardResult.getPaymentCardExpirationYear()),
				String.valueOf(paymentCardResult.getPaymentCardExpirationMonth())
		);
	}

	protected Component getRoot() {
		return widgetUtils.getRoot();
	}
}
