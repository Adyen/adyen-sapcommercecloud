package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import com.adyen.model.*;
import com.adyen.service.*;
import com.google.gson.Gson;
import com.hybris.cockpitng.actions.*;
import com.hybris.cockpitng.engine.impl.*;
import de.hybris.platform.cissapdigitalpayment.model.*;
import de.hybris.platform.core.model.order.*;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.config.*;
import de.hybris.platform.servicelayer.model.*;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;

import org.zkoss.zul.Messagebox;


public class RegisterCardAuthDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	@Resource
	private AdyenSapDigitalPaymentService adyenSapDigitalPaymentService;

	@Resource
	private ModelService modelService;

	@Resource
	private ConfigurationService configurationService;

	final static String PAYMENT_SERVICE_PROVIDER = "Payment service provider: ";
	final static String DIGITAL_PAYMENT_TRANSACTION = "Digital payment transaction: ";
	final static String DIGITAL_PAYT_TRANSACTION_RESULT = "Digital payt transaction result: ";
	final static String DIGITAL_PAYT_TRANS_RSLT_DESC = "DigitalPaytTransRsltDesc : ";
	final static String PAYT_CARD_BY_PAYT_SERVICE_PROVIDER = "PaytCardByPaytServiceProvider: ";
	final static String RAW_DATA = "Raw data: ";
	final static String REGISTER_AUTHORIZATION = "Register Authorization";

	@Override
	public ActionResult<Object> perform(ActionContext<OrderModel> actionContext)
	{

		final OrderModel orderModel = actionContext.getData();
		final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfigurationModel = orderModel.getStore()
				.getSapDigitalPaymentConfiguration();
		List<PaymentTransactionModel> paymentTransactions = orderModel.getPaymentTransactions();

		final String adyenPSP = configurationService.getConfiguration().getString("adyen.psp");
		final String adyenAuthorizationType = configurationService.getConfiguration().getString("adyen.authorizationType");

		final DigitalPaymentGetAuthorizationList digitalPaymentGetAuthorizationList = new DigitalPaymentGetAuthorizationList();
		final DigitalPaymentGetAuthorization digitalPaymentGetAuthorization = new DigitalPaymentGetAuthorization();
		if(getCardAuth(paymentTransactions).isPresent()) {
			digitalPaymentGetAuthorization.setAuthorizationByPaytSrvcPrvdr(getCardAuth(paymentTransactions).get().getRequestId());
		}else {
			Messagebox.show("AUTHORIZATION transaction not found.", "Error Details", new Messagebox.Button[]
					{ Messagebox.Button.OK, Messagebox.Button.CANCEL }, null, null, null);
		}

		digitalPaymentGetAuthorization.setAuthorizationCurrency(getCardAuth(paymentTransactions).get().getCurrency().getIsocode());
		digitalPaymentGetAuthorization.setAuthorizedAmountInAuthznCrcy(getCardAuth(paymentTransactions).get().getAmount().toString());
		digitalPaymentGetAuthorization.setDigitalPaymentAuthorizationType(adyenAuthorizationType);
		digitalPaymentGetAuthorization.setMerchantAccount(orderModel.getStore().getAdyenMerchantAccount());
		digitalPaymentGetAuthorization.setPaymentServiceProvider(adyenPSP);
		digitalPaymentGetAuthorizationList.setAuthorizations(List.of(digitalPaymentGetAuthorization));


		final DigitalPaymentGetAuthorizationResultList resultModelList = adyenSapDigitalPaymentService.getForPaymentAuthorization(
				digitalPaymentGetAuthorizationList, sapDigitalPaymentConfigurationModel);

		final Optional<DigitalPaymentGetAuthorizationResult> resultModel = resultModelList.getAuthorizationResults().stream().findFirst();

		final DPAOperationResultModel dpaOperationResultModel = modelService.create(DPAOperationResultModel._TYPECODE);

		final StringBuilder dataToDisplay = new StringBuilder();

		if (resultModel.isPresent()) {
			dataToDisplay.append(String.format("%s:%s\n", PAYMENT_SERVICE_PROVIDER, resultModel.get().getPaymentServiceProvider()));
			dataToDisplay.append(String.format("%s:%s\n", DIGITAL_PAYMENT_TRANSACTION, resultModel.get().getDigitalPaymentTransaction()));
			dataToDisplay.append(String.format("%s:%s\n", DIGITAL_PAYT_TRANSACTION_RESULT, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult()));
			dataToDisplay.append(String.format("%s:%s\n", DIGITAL_PAYT_TRANS_RSLT_DESC, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()));
			dataToDisplay.append(String.format("%s:%s\n", PAYT_CARD_BY_PAYT_SERVICE_PROVIDER, resultModel.get().getPaytCardByPaytServiceProvider()));
		} else {
			Messagebox.show("Result model is empty.", "Error Details", new Messagebox.Button[]
					{ Messagebox.Button.OK, Messagebox.Button.CANCEL }, null, null, null);
			return new ActionResult<>(ActionResult.ERROR);
		}

			final StringBuilder rawRequestData = new StringBuilder();
			rawRequestData.append(RAW_DATA + new Gson().toJson(resultModel));
			orderModel.getPaymentInfo().setAdyenDPAAuthorizationCode(resultModel.get().getAuthorization().getAuthorizationByDigitalPaytSrvc());
			modelService.save(orderModel.getPaymentInfo());
			dpaOperationResultModel.setDpaOperation(REGISTER_AUTHORIZATION);
			dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
			dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
			dpaOperationResultModel.setAdyenDPAAuthorizationCode(resultModel.get().getAuthorization() != null ? resultModel.get().getAuthorization().getAuthorizationByDigitalPaytSrvc() : "");
			dpaOperationResultModel.setDpaResult(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult());
			dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
			dpaOperationResultModel.setDpaOperationPayload(rawRequestData.toString());
			modelService.save(dpaOperationResultModel);

		if (dpaOperationResultModel.getDpaResult().equals("01")) {
			Messagebox.show(dataToDisplay.toString(), "Card authorization registered successfully in DPA.", new Messagebox.Button[]
					{Messagebox.Button.OK, Messagebox.Button.CANCEL}, null, null, null);
			return new ActionResult<>(ActionResult.SUCCESS, resultModelList);
		} else {
			Messagebox.show(dpaOperationResultModel.getDpaOperationResultDesc(), "Error Details", new Messagebox.Button[]
					{Messagebox.Button.OK, Messagebox.Button.CANCEL}, null, null, null);
			return new ActionResult<>(ActionResult.ERROR);
		}
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
