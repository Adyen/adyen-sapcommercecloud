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
import org.zkoss.zul.*;

import javax.annotation.*;
import java.time.*;
import java.util.*;


public class RegisterCaptureWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object>
{

	final static String MERCHANT_ACCOUNT = "MerchantAccount:  %s \n";
	final static String PAYMENT_SERVICE_PROVIDER = "PaymentServiceProvider: %s \n";
	final static String PAYMENT_BY_PAYMENT_SERVICE_PRVDR = "PaymentByPaymentServicePrvdr : %s \n";
	final static String TRANSACTION_RESULT_DESCRIPTION = "Transaction Result Description: %s \n";
	final static String TRANSACTION_RESULT = "Transaction result: %s \n";
	final static String RAW_DATA = "Raw data: %s";
	final static String DPA_OPERATION = "Register Capture";

	@Resource
	private AdyenSapDigitalPaymentService adyenSapDigitalPaymentService;

	@Resource
	private ModelService modelService;

	@Resource
	private ConfigurationService configurationService;

	@Override
	public ActionResult<Object> perform(final ActionContext<OrderModel> actionContext)
	{

		final OrderModel orderModel = actionContext.getData();
		final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfigurationModel = orderModel.getStore()
				.getSapDigitalPaymentConfiguration();

		final List<PaymentTransactionModel> paymentTransactions = orderModel.getPaymentTransactions();
		final String adyenPSP = configurationService.getConfiguration().getString("adyen.psp");
		final String adyenDirectCaptureType = configurationService.getConfiguration().getString("adyen.directCaptureType");



		final DigitalPaymentGetCaptureList captureList = new DigitalPaymentGetCaptureList();

		final DigitalPaymentGetCapture capture = new DigitalPaymentGetCapture();
		capture.setMerchantAccount(orderModel.getStore().getAdyenMerchantAccount());
		capture.setDigitalPaymentDirectCaptureType(adyenDirectCaptureType);
		capture.setPaymentServiceProvider(adyenPSP);
		capture.setPaymentCurrency(orderModel.getCurrency().getIsocode());
		capture.setAmountInPaymentCurrency(orderModel.getTotalPrice().toString());

		if(getCapturePayment(paymentTransactions).isPresent())
		{
			capture.setPaymentByPaymentServicePrvdr(getCapturePayment(paymentTransactions).get().getRequestId());
		}
		else
		{
			Messagebox.show("No Capture Transaction", "Error Details", new Messagebox.Button[]
					{ Messagebox.Button.OK, Messagebox.Button.CANCEL }, null, null, null);
			return new ActionResult<>(ActionResult.ERROR);
		}
		captureList.setCaptures(List.of(capture));

		final DigitalPaymentGetCaptureResultList resultModelList = adyenSapDigitalPaymentService.getForDirectCapture(
				captureList, sapDigitalPaymentConfigurationModel);

		final DPAOperationResultModel dpaOperationResultModel = modelService.create(DPAOperationResultModel._TYPECODE);

		final StringBuilder dataToDisplay = new StringBuilder();


			final Optional<DigitalPaymentGetCaptureResultModel> resultModel = resultModelList.getCaptures().stream().findFirst();

			if(resultModel.isPresent())
			{

				dataToDisplay.append(String.format(MERCHANT_ACCOUNT, resultModel.get().getMerchantAccount()));
				dataToDisplay.append(String.format(PAYMENT_SERVICE_PROVIDER, resultModel.get().getPaymentServiceProvider()));
				dataToDisplay.append(String.format(PAYMENT_BY_PAYMENT_SERVICE_PRVDR, resultModel.get().getPaymentByPaymentServicePrvdr()));
				dataToDisplay.append(String.format(TRANSACTION_RESULT_DESCRIPTION, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()));
				dataToDisplay.append(String.format(TRANSACTION_RESULT, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult()));
			}
			else {
				Messagebox.show("Result model is empty", "Error Details", new Messagebox.Button[]
						{ Messagebox.Button.OK, Messagebox.Button.CANCEL }, null, null, null);
				return new ActionResult<>(ActionResult.ERROR);
			}


			final StringBuilder rawRequestData = new StringBuilder();
				rawRequestData.append(String.format(RAW_DATA, new Gson().toJson(resultModel.get())));
				orderModel.getPaymentInfo().setAdyenDPAChargeTransactionId(resultModel.get().getDigitalPaymentTransaction() != null ? resultModel.get().getDigitalPaymentTransaction().getDigitalPaymentTransaction() : "");
				modelService.save(orderModel.getPaymentInfo());
				dpaOperationResultModel.setDpaOperation(DPA_OPERATION);
				dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
				dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction() != null ? resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc() : "");
				dpaOperationResultModel.setAdyenDPAChargeTransactionId(resultModel.get().getDigitalPaymentTransaction() != null ? resultModel.get().getDigitalPaymentTransaction().getDigitalPaymentTransaction() : "");
				dpaOperationResultModel.setDpaResult(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult());
				dpaOperationResultModel.setDpaOperationPayload(rawRequestData.toString());
				dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
				modelService.save(dpaOperationResultModel);
				if(dpaOperationResultModel.getDpaResult().equals("01"))
				{
					Messagebox.show(dataToDisplay.toString(), "Capture registered successfully in DPA.", new Messagebox.Button[]
							{ Messagebox.Button.OK, Messagebox.Button.CANCEL }, null, null, null);
					return new ActionResult<>(ActionResult.SUCCESS, resultModelList);
				}
				else
				{
					Messagebox.show(dpaOperationResultModel.getDpaOperationResultDesc(), "Error Details", new Messagebox.Button[]
							{ Messagebox.Button.OK, Messagebox.Button.CANCEL }, null, null, null);
					return new ActionResult<>(ActionResult.ERROR);
				}
	}

	@Override
	public boolean canPerform(final ActionContext<OrderModel> ctx)
	{
		final OrderModel orderModel = ctx.getData();
		return getCapturePayment(orderModel.getPaymentTransactions()).isPresent();
	}

	private Optional<PaymentTransactionEntryModel> getCapturePayment(List<PaymentTransactionModel> paymentTransactions)
	{
		return paymentTransactions.stream()
				.flatMap(tx -> tx.getEntries().stream())
				.filter(entry -> PaymentTransactionType.CAPTURE.equals(entry.getType()))
				.findFirst();
	}
}

