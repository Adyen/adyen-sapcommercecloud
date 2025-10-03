package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.CAPTURE_REGISTERED_SUCCESSFULLY_IN_DPA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.DIGITAL_PAYT_TRANS_RSLT_DESC;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.DPA_SUCCESS_RESULT;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.MERCHANT_ACCOUNT;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.NO_CAPTURE_TRANSACTION;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.PAYMENT_BY_PAYMENT_SERVICE_PRVDR;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.PAYMENT_SERVICE_PROVIDER;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RAW_DATA;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.REGISTER_CAPTURE;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RESULT_MODEL_IS_EMPTY;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showError;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil.showMessageBox;

import de.hybris.platform.cissapdigitalpayment.model.SAPDigitalPaymentConfigurationModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;

import com.adyen.model.DPAOperationResultModel;
import com.adyen.model.DigitalPaymentGetCapture;
import com.adyen.model.DigitalPaymentGetCaptureList;
import com.adyen.model.DigitalPaymentGetCaptureResultList;
import com.adyen.model.DigitalPaymentGetCaptureResultModel;
import com.adyen.service.AdyenSapDigitalPaymentService;
import com.google.gson.Gson;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;

public class RegisterCaptureWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	@Resource
	private AdyenSapDigitalPaymentService adyenSapDigitalPaymentService;

	@Resource
	private ModelService modelService;

	@Resource
	private ConfigurationService configurationService;

	@Override
	public ActionResult<Object> perform(final ActionContext<OrderModel> actionContext) {
		final OrderModel orderModel = actionContext.getData();
		final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfigurationModel = orderModel.getStore()
				.getSapDigitalPaymentConfiguration();
		final List<PaymentTransactionModel> paymentTransactions = orderModel.getPaymentTransactions();
		final String adyenPSP = configurationService.getConfiguration().getString("adyen.psp");
		final String adyenDirectCaptureType = configurationService.getConfiguration().getString("adyen.directCaptureType");

		final Optional<DigitalPaymentGetCaptureList> captureListOpt =
				buildCaptureList(orderModel, adyenPSP, adyenDirectCaptureType);
		if (captureListOpt.isEmpty()) {
			return showError(NO_CAPTURE_TRANSACTION);
		}
		final DigitalPaymentGetCaptureResultList resultModelList =
				adyenSapDigitalPaymentService.getForDirectCapture(captureListOpt.get(), sapDigitalPaymentConfigurationModel);

		final DigitalPaymentGetCaptureList captureList = new DigitalPaymentGetCaptureList();

		final Optional<DigitalPaymentGetCaptureResultModel> optionalResult = resultModelList.getCaptures().stream().findFirst();

		if (optionalResult.isEmpty()) {
			return showError(RESULT_MODEL_IS_EMPTY);
		}
		final DigitalPaymentGetCaptureResultModel result = optionalResult.get();
		final String displayData = buildDisplayData(result);

		orderModel.getPaymentInfo().setAdyenDPAChargeTransactionId(result.getDigitalPaymentTransaction() != null ? result.getDigitalPaymentTransaction().getDigitalPaymentTransaction() : "");
		modelService.save(orderModel.getPaymentInfo());

		final DPAOperationResultModel dpaOperationResultModel = buildCaptureResultModel(result);

		modelService.save(dpaOperationResultModel);
		if (dpaOperationResultModel.getDpaResult().equals(DPA_SUCCESS_RESULT)) {
			showMessageBox(displayData, CAPTURE_REGISTERED_SUCCESSFULLY_IN_DPA);
			return new ActionResult<>(ActionResult.SUCCESS, resultModelList);
		} else {
			return showError(dpaOperationResultModel.getDpaOperationResultDesc());
		}
	}

	private DPAOperationResultModel buildCaptureResultModel(DigitalPaymentGetCaptureResultModel result) {
		final StringBuilder rawRequestData = new StringBuilder();
		rawRequestData.append(String.format(RAW_DATA, new Gson().toJson(result)));
		final DPAOperationResultModel dpaOperationResultModel = modelService.create(DPAOperationResultModel._TYPECODE);
		dpaOperationResultModel.setDpaOperation(REGISTER_CAPTURE);
		dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResultModel.setDpaOperationResultDesc(result.getDigitalPaymentTransaction() != null ? result.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc() : "");
		dpaOperationResultModel.setAdyenDPAChargeTransactionId(result.getDigitalPaymentTransaction() != null ? result.getDigitalPaymentTransaction().getDigitalPaymentTransaction() : "");
		dpaOperationResultModel.setDpaResult(result.getDigitalPaymentTransaction().getDigitalPaytTransResult());
		dpaOperationResultModel.setDpaOperationPayload(rawRequestData.toString());
		return dpaOperationResultModel;
	}

	@Override
	public boolean canPerform(final ActionContext<OrderModel> ctx) {
		final OrderModel orderModel = ctx.getData();
		return getCapturePayment(orderModel.getPaymentTransactions()).isPresent();
	}

	private Optional<PaymentTransactionEntryModel> getCapturePayment(List<PaymentTransactionModel> paymentTransactions) {
		return paymentTransactions.stream()
				.flatMap(tx -> tx.getEntries().stream())
				.filter(entry -> PaymentTransactionType.CAPTURE.equals(entry.getType()))
				.findFirst();
	}

	private Optional<DigitalPaymentGetCaptureList> buildCaptureList(
			final OrderModel order,
			final String adyenPSP,
			final String directCaptureType
	) {
		final Optional<PaymentTransactionEntryModel> captureEntryOpt =
				getCapturePayment(order.getPaymentTransactions());

		if (captureEntryOpt.isEmpty()) {
			return Optional.empty();
		}

		final DigitalPaymentGetCapture capture = buildCapture(order, adyenPSP, directCaptureType, captureEntryOpt.get());

		final DigitalPaymentGetCaptureList captureList = new DigitalPaymentGetCaptureList();
		captureList.setCaptures(List.of(capture));
		return Optional.of(captureList);
	}

	private static DigitalPaymentGetCapture buildCapture(OrderModel order, String adyenPSP, String directCaptureType, PaymentTransactionEntryModel captureEntry) {
		final DigitalPaymentGetCapture capture = new DigitalPaymentGetCapture();
		capture.setMerchantAccount(order.getStore().getAdyenMerchantAccount());
		capture.setDigitalPaymentDirectCaptureType(directCaptureType);
		capture.setPaymentServiceProvider(adyenPSP);
		capture.setPaymentCurrency(order.getCurrency().getIsocode());
		capture.setAmountInPaymentCurrency(order.getTotalPrice().toString());
		capture.setPaymentByPaymentServicePrvdr(captureEntry.getRequestId());
		return capture;
	}

	private String buildDisplayData(DigitalPaymentGetCaptureResultModel resultModel) {
		final StringBuilder dataToDisplay = new StringBuilder();

		dataToDisplay.append(String.format(MERCHANT_ACCOUNT, resultModel.getMerchantAccount()));
		dataToDisplay.append(String.format(PAYMENT_SERVICE_PROVIDER, resultModel.getPaymentServiceProvider()));
		dataToDisplay.append(String.format(PAYMENT_BY_PAYMENT_SERVICE_PRVDR, resultModel.getPaymentByPaymentServicePrvdr()));
		dataToDisplay.append(String.format(DIGITAL_PAYT_TRANS_RSLT_DESC, resultModel.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()));
		dataToDisplay.append(String.format(DIGITAL_PAYT_TRANS_RSLT_DESC, resultModel.getDigitalPaymentTransaction().getDigitalPaytTransResult()));
		return dataToDisplay.toString();
	}
}