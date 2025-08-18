package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import com.adyen.model.*;
import com.adyen.service.*;
import com.google.gson.*;
import com.hybris.cockpitng.actions.*;
import com.hybris.cockpitng.engine.impl.*;
import de.hybris.platform.cissapdigitalpayment.model.*;
import de.hybris.platform.core.model.order.*;
import de.hybris.platform.core.model.user.*;
import de.hybris.platform.servicelayer.config.*;
import de.hybris.platform.servicelayer.model.*;

import javax.annotation.*;
import java.time.*;
import java.util.*;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.*;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.*;

public class RegisterCardWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {


	@Resource
	private AdyenSapDigitalPaymentService adyenSapDigitalPaymentService;

	@Resource
	private ModelService modelService;

	@Resource
	private ConfigurationService configurationService;

	@Override
	public ActionResult<Object> perform(ActionContext<OrderModel> actionContext) {

		final OrderModel orderModel = actionContext.getData();
		final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfigurationModel = orderModel.getStore()
				.getSapDigitalPaymentConfiguration();
		final DigitalGetPaymentCardContext context = new DigitalGetPaymentCardContext();
		final CustomerModel customerModel = (CustomerModel) orderModel.getUser();
		context.setShopperReference(customerModel.getCustomerID());
		final String adyenPSP = configurationService.getConfiguration().getString("adyen.psp");
		final DigitalGetPaymentCardList digitalGetPaymentCardList = buildPaymentCardList(orderModel, context, adyenPSP);
		final DigitalPaymentsCardResultList resultModelList = adyenSapDigitalPaymentService.getForPaymentCard(
				digitalGetPaymentCardList, sapDigitalPaymentConfigurationModel);
		final Optional<DigitalPaymentsCardResultModel> resultModel = resultModelList.getDigitalPaymentsCardResultModels().stream().findFirst();

		if (resultModel.isEmpty()) {
			return prepareErrorActionResult(RESULT_MODEL_IS_EMPTY);
		}

		final String dataToDisplay = buildDisplayData(resultModel);

		orderModel.getPaymentInfo().setAdyenDPAPaymentCardToken(resultModel.get().getSource() != null ? resultModel.get().getSource().getCard() != null ? resultModel.get().getSource().getCard().getPaytCardByDigitalPaymentSrvc() : "" : "");
		modelService.save(orderModel.getPaymentInfo());

		final DPAOperationResultModel dpaOperationResultModel = buildCardResultModel(resultModel);
		modelService.save(dpaOperationResultModel);
		if (dpaOperationResultModel.getDpaResult().equals(DPA_SUCCESS_RESULT)) {
			showMessageBox(dataToDisplay.toString(), "Card registered successfully in DPA.");
			return new ActionResult<>(ActionResult.SUCCESS, resultModelList);
		} else {
			return prepareErrorActionResult(dpaOperationResultModel.getDpaOperationResultDesc());
		}
	}

	private static ActionResult<Object> prepareErrorActionResult(String message) {
		showMessageBox(message, ERROR_DETAILS);
		return new ActionResult<>(ActionResult.ERROR);
	}

	private DPAOperationResultModel buildCardResultModel(final Optional<DigitalPaymentsCardResultModel> resultModel) {
		final DPAOperationResultModel dpaOperationResultModel = modelService.create(DPAOperationResultModel._TYPECODE);
		final StringBuilder rawRequestData = new StringBuilder();
		rawRequestData.append(String.format(RAW_DATA, new Gson().toJson(resultModel)));
		dpaOperationResultModel.setDpaOperation(REGISTER_CARD);
		dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
		dpaOperationResultModel.setAdyenDPAPaymentCardToken(resultModel.get().getSource().getCard().getPaytCardByDigitalPaymentSrvc());
		dpaOperationResultModel.setDpaOperationPayload(rawRequestData.toString());
		dpaOperationResultModel.setDpaResult(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult());
		return dpaOperationResultModel;
	}

	private static String buildDisplayData(final Optional<DigitalPaymentsCardResultModel> resultModel) {
		final StringBuilder dataToDisplay = new StringBuilder();
		dataToDisplay.append(String.format(PAYMENT_SERVICE_PROVIDER, resultModel.get().getPaymentServiceProvider()));
		dataToDisplay.append(String.format(DIGITAL_PAYMENT_TRANSACTION, resultModel.get().getPaymentServiceProvider()));
		dataToDisplay.append(String.format(DIGITAL_PAYT_TRANSACTION_RESULT, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult()));
		dataToDisplay.append(String.format(DIGITAL_PAYT_TRANS_RSLT_DESC, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()));
		dataToDisplay.append(String.format(PAYT_CARD_BY_PAYT_SERVICE_PROVIDER, resultModel.get().getPaytCardByPaytServiceProvider()));
		return dataToDisplay.toString();
	}

	private static DigitalGetPaymentCardList buildPaymentCardList(final OrderModel orderModel, final DigitalGetPaymentCardContext context, final String adyenPSP) {
		final DigitalGetPaymentCardList digitalGetPaymentCardList = new DigitalGetPaymentCardList();
		final DigitalGetPaymentCard digitalGetPaymentCard = new DigitalGetPaymentCard();
		digitalGetPaymentCard.setMerchantAccount(orderModel.getStore().getAdyenMerchantAccount());
		digitalGetPaymentCard.setPaymentCardContext(context);
		digitalGetPaymentCard.setPaymentServiceProvider(adyenPSP);
		digitalGetPaymentCard.setPaytCardByPaytServiceProvider(orderModel.getPaymentInfo().getAdyenPaymentCardToken());
		digitalGetPaymentCardList.setDigitalGetPaymentCardRequests(List.of(digitalGetPaymentCard));
		return digitalGetPaymentCardList;
	}

	@Override
	public boolean canPerform(ActionContext<OrderModel> ctx) {
		return true;
	}


}
