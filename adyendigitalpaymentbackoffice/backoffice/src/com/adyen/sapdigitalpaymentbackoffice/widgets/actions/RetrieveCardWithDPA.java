package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import com.adyen.model.*;
import com.adyen.service.*;
import com.google.gson.*;
import com.hybris.cockpitng.actions.*;
import com.hybris.cockpitng.engine.impl.*;
import com.hybris.cockpitng.util.*;
import de.hybris.platform.cissapdigitalpayment.model.*;
import de.hybris.platform.core.model.order.*;
import de.hybris.platform.servicelayer.model.*;
import org.zkoss.zk.ui.*;
import org.zkoss.zul.*;

import javax.annotation.*;
import java.time.*;
import java.util.*;

public class RetrieveCardWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	private static final String DPA_OPERATION = "RetrieveCardWithDPA";
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
			final DPAOperationResultModel dpaOperationResult = new DPAOperationResultModel();
			dataToSaveToModel.append("Raw data:").append(new Gson().toJson(paymentCardResult));
			dpaOperationResult.setDpaOperationPayload(dataToSaveToModel.toString());
			dpaOperationResult.setDpaOperation(DPA_OPERATION);
			dpaOperationResult.setDpaOperationTime(Date.from(Instant.now()));
			dpaOperationResult.setDpaCardReference(paymentCardResult.getPaytCardByDigitalPaymentSrvc());
			modelService.save(dpaOperationResult);

			final String formatted = prepareCardInfo(paymentCardResult);
			dataToDisplay.append(formatted);

		} else {

			dataToDisplay.append("Card doesn't exist!!! ");
		}


		Messagebox.show(dataToDisplay.toString(), "Retrived Card Details", new Messagebox.Button[]
				{Messagebox.Button.OK, Messagebox.Button.CANCEL}, null, null, null);

		return new ActionResult(ActionResult.SUCCESS);
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
