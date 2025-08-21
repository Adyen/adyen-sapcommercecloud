package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import com.adyen.model.*;
import com.adyen.service.*;
import com.google.gson.*;
import com.hybris.cockpitng.actions.*;
import com.hybris.cockpitng.engine.impl.*;
import de.hybris.platform.cissapdigitalpayment.model.*;
import de.hybris.platform.core.model.order.*;
import de.hybris.platform.payment.enums.*;
import de.hybris.platform.payment.model.*;
import de.hybris.platform.servicelayer.config.*;
import de.hybris.platform.servicelayer.model.*;

import javax.annotation.*;
import java.time.*;
import java.util.*;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.*;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.*;

public class RegisterCardAuthDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

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
		List<PaymentTransactionModel> paymentTransactions = orderModel.getPaymentTransactions();

		final String adyenPSP = configurationService.getConfiguration().getString("adyen.psp");
		final String adyenAuthorizationType = configurationService.getConfiguration().getString("adyen.authorizationType");

		if (getCardAuth(paymentTransactions).isEmpty()) {
			return showError(AUTHORIZATION_TRANSACTION_NOT_FOUND);
		}
		final DigitalPaymentGetAuthorizationList digitalPaymentGetAuthorizationList = buildAuthorizationList(paymentTransactions, adyenAuthorizationType, orderModel, adyenPSP);

		final DigitalPaymentGetAuthorizationResultList resultModelList = adyenSapDigitalPaymentService.getForPaymentAuthorization(
				digitalPaymentGetAuthorizationList, sapDigitalPaymentConfigurationModel);

		final Optional<DigitalPaymentGetAuthorizationResult> resultModel = resultModelList.getAuthorizationResults().stream().findFirst();

		if (resultModel.isEmpty()) {
			return showError(RESULT_MODEL_IS_EMPTY);
		}

		final String dataToDisplay = buildDisplayData(resultModel);
		orderModel.getPaymentInfo().setAdyenDPAAuthorizationCode(resultModel.get().getAuthorization().getAuthorizationByDigitalPaytSrvc());
		modelService.save(orderModel.getPaymentInfo());
		final DPAOperationResultModel dpaOperationResultModel = buildAuthorizationResultModel(resultModel);
		modelService.save(dpaOperationResultModel);

		if (dpaOperationResultModel.getDpaResult().equals(DPA_SUCCESS_RESULT)) {
			showMessageBox(dataToDisplay.toString(), CARD_AUTHORIZATION_REGISTERED_SUCCESSFULLY_IN_DPA);
			return new ActionResult<>(ActionResult.SUCCESS, resultModelList);
		} else {
			return showError(dpaOperationResultModel.getDpaOperationResultDesc());
		}
	}

	private String buildDisplayData(final Optional<DigitalPaymentGetAuthorizationResult> resultModel) {
		final StringBuilder dataToDisplay = new StringBuilder();
		dataToDisplay.append(String.format(PAYMENT_SERVICE_PROVIDER, resultModel.get().getPaymentServiceProvider()));
		dataToDisplay.append(String.format(DIGITAL_PAYMENT_TRANSACTION, resultModel.get().getDigitalPaymentTransaction()));
		dataToDisplay.append(String.format(DIGITAL_PAYT_TRANSACTION_RESULT, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult()));
		dataToDisplay.append(String.format(DIGITAL_PAYT_TRANS_RSLT_DESC, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()));
		dataToDisplay.append(String.format(PAYT_CARD_BY_PAYT_SERVICE_PROVIDER, resultModel.get().getPaytCardByPaytServiceProvider()));
		return dataToDisplay.toString();
	}

	private DigitalPaymentGetAuthorizationList buildAuthorizationList(final List<PaymentTransactionModel> paymentTransactions, final String adyenAuthorizationType, final OrderModel orderModel, final String adyenPSP) {
		final DigitalPaymentGetAuthorizationList digitalPaymentGetAuthorizationList = new DigitalPaymentGetAuthorizationList();
		final DigitalPaymentGetAuthorization digitalPaymentGetAuthorization = new DigitalPaymentGetAuthorization();
		digitalPaymentGetAuthorization.setAuthorizationByPaytSrvcPrvdr(getCardAuth(paymentTransactions).get().getRequestId());
		digitalPaymentGetAuthorization.setAuthorizationCurrency(getCardAuth(paymentTransactions).get().getCurrency().getIsocode());
		digitalPaymentGetAuthorization.setAuthorizedAmountInAuthznCrcy(getCardAuth(paymentTransactions).get().getAmount().toString());
		digitalPaymentGetAuthorization.setDigitalPaymentAuthorizationType(adyenAuthorizationType);
		digitalPaymentGetAuthorization.setMerchantAccount(orderModel.getStore().getAdyenMerchantAccount());
		digitalPaymentGetAuthorization.setPaymentServiceProvider(adyenPSP);
		digitalPaymentGetAuthorizationList.setAuthorizations(List.of(digitalPaymentGetAuthorization));
		return digitalPaymentGetAuthorizationList;
	}

	private DPAOperationResultModel buildAuthorizationResultModel(final Optional<DigitalPaymentGetAuthorizationResult> resultModel) {
		final StringBuilder rawRequestData = new StringBuilder();
		rawRequestData.append(RAW_DATA + new Gson().toJson(resultModel));
		final DPAOperationResultModel dpaOperationResultModel = modelService.create(DPAOperationResultModel._TYPECODE);
		dpaOperationResultModel.setDpaOperation(REGISTER_AUTHORIZATION);
		dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
		dpaOperationResultModel.setAdyenDPAAuthorizationCode(resultModel.get().getAuthorization() != null ? resultModel.get().getAuthorization().getAuthorizationByDigitalPaytSrvc() : "");
		dpaOperationResultModel.setDpaResult(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult());
		dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
		dpaOperationResultModel.setDpaOperationPayload(rawRequestData.toString());
		return dpaOperationResultModel;
	}

	@Override
	public boolean canPerform(ActionContext<OrderModel> ctx) {
		return getCardAuth(ctx.getData().getPaymentTransactions()).isPresent();
	}


	private Optional<PaymentTransactionEntryModel> getCardAuth(List<PaymentTransactionModel> paymentTransactions) {
		return paymentTransactions.stream()
				.flatMap(tx -> tx.getEntries().stream())
				.filter(entry -> PaymentTransactionType.AUTHORIZATION.equals(entry.getType()))
				.findFirst();
	}

}
