package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.CARD_REGISTERED_SUCCESSFULLY_IN_DPA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.DIGITAL_PAYMENT_TRANSACTION;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.DIGITAL_PAYT_TRANSACTION_RESULT;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.DIGITAL_PAYT_TRANS_RSLT_DESC;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.DPA_SUCCESS_RESULT;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.PAYMENT_SERVICE_PROVIDER;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.PAYT_CARD_BY_PAYT_SERVICE_PROVIDER;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RAW_DATA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.REGISTER_CARD;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RESULT_MODEL_IS_EMPTY;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showError;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showMessageBox;

import de.hybris.platform.cissapdigitalpayment.model.SAPDigitalPaymentConfigurationModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;

import com.adyen.model.DPAOperationResultModel;
import com.adyen.model.DigitalGetPaymentCard;
import com.adyen.model.DigitalGetPaymentCardContext;
import com.adyen.model.DigitalGetPaymentCardList;
import com.adyen.model.DigitalPaymentsCardResultList;
import com.adyen.model.DigitalPaymentsCardResultModel;
import com.adyen.service.AdyenSapDigitalPaymentService;
import com.google.gson.Gson;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;

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
			return showError(RESULT_MODEL_IS_EMPTY);
		}

		final String dataToDisplay = buildDisplayData(resultModel);

		orderModel.getPaymentInfo().setAdyenDPAPaymentCardToken(resultModel.get().getSource() != null ? resultModel.get().getSource().getCard() != null ? resultModel.get().getSource().getCard().getPaytCardByDigitalPaymentSrvc() : "" : "");
		modelService.save(orderModel.getPaymentInfo());

		final DPAOperationResultModel dpaOperationResultModel = buildCardResultModel(resultModel);
		modelService.save(dpaOperationResultModel);
		if (dpaOperationResultModel.getDpaResult().equals(DPA_SUCCESS_RESULT)) {
			showMessageBox(dataToDisplay.toString(), CARD_REGISTERED_SUCCESSFULLY_IN_DPA);
			return new ActionResult<>(ActionResult.SUCCESS, resultModelList);
		} else {
			return showError(dpaOperationResultModel.getDpaOperationResultDesc());
		}
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
