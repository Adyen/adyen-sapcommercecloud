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
			return prepareErrorActionResult("No Capture Transaction");
		}
		final DigitalPaymentGetCaptureResultList resultModelList =
				adyenSapDigitalPaymentService.getForDirectCapture(captureListOpt.get(), sapDigitalPaymentConfigurationModel);

		final DigitalPaymentGetCaptureList captureList = new DigitalPaymentGetCaptureList();

		final Optional<DigitalPaymentGetCaptureResultModel> optionalResult = resultModelList.getCaptures().stream().findFirst();

		if (optionalResult.isEmpty()) {
			return prepareErrorActionResult(RESULT_MODEL_IS_EMPTY);
		}
		final DigitalPaymentGetCaptureResultModel result = optionalResult.get();
		final String displayData = buildDisplayData(result);

		orderModel.getPaymentInfo().setAdyenDPAChargeTransactionId(result.getDigitalPaymentTransaction() != null ? result.getDigitalPaymentTransaction().getDigitalPaymentTransaction() : "");
		modelService.save(orderModel.getPaymentInfo());

		final DPAOperationResultModel dpaOperationResultModel = buildCaptureResultModel(result);

		modelService.save(dpaOperationResultModel);
		if (dpaOperationResultModel.getDpaResult().equals(DPA_SUCCESS_RESULT)) {
			showMessageBox(displayData, "Capture registered successfully in DPA.");
			return new ActionResult<>(ActionResult.SUCCESS, resultModelList);
		} else {
			return prepareErrorActionResult(dpaOperationResultModel.getDpaOperationResultDesc());
		}
	}

	private static ActionResult<Object> prepareErrorActionResult(String message) {
		showMessageBox(message, ERROR_DETAILS);
		return new ActionResult<>(ActionResult.ERROR);
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