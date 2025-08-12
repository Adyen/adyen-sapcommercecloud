package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import com.adyen.model.*;
import com.adyen.service.*;
import com.google.gson.*;
import com.hybris.cockpitng.actions.*;
import com.hybris.cockpitng.engine.impl.*;

import de.hybris.platform.cissapdigitalpayment.model.*;
import de.hybris.platform.core.model.order.*;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.*;
import de.hybris.platform.servicelayer.model.*;

import org.zkoss.zul.*;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.*;


public class RegisterCardWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	@Resource
	private AdyenSapDigitalPaymentService adyenSapDigitalPaymentService;

	@Resource
	private ModelService modelService;

	@Resource
	private ConfigurationService configurationService;


	final static String PAYMENT_SERVICE_PROVIDER = "Payment service provider: %s \n";
	final static String DIGITAL_PAYMENT_TRANSACTION = "Payment service provider: %s \n";
	final static String DIGITAL_PAYT_TRANSACTIONRESULT = "Digital payt transaction result: %s \n";
	final static String DIGITAL_PAYT_TRANSRSLT_DESC = "DigitalPaytTransRsltDesc : %s \n";
	final static String PAYT_CARD_BY_PAYT_SERVICE_PROVIDER = "PaytCardByPaytServiceProvider: %s \n";
	final static String RAW_DATA = "RAWDATA : %s";
	final static String DPA_OPERATION = "Register Card";



	@Override
	public ActionResult<Object> perform(ActionContext<OrderModel> actionContext)
	{

		final OrderModel orderModel = actionContext.getData();
		final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfigurationModel = orderModel.getStore()
				.getSapDigitalPaymentConfiguration();
		final DigitalGetPaymentCardContext context = new DigitalGetPaymentCardContext();
		final CustomerModel customerModel = (CustomerModel) orderModel.getUser();
		context.setShopperReference(customerModel.getCustomerID());
		final String adyenPSP = configurationService.getConfiguration().getString("adyen.psp");


		final DigitalGetPaymentCardList digitalGetPaymentCardList = new DigitalGetPaymentCardList();
		final DigitalGetPaymentCard digitalGetPaymentCard = new DigitalGetPaymentCard();
		digitalGetPaymentCard.setMerchantAccount(orderModel.getStore().getAdyenMerchantAccount());
		digitalGetPaymentCard.setPaymentCardContext(context);
		digitalGetPaymentCard.setPaymentServiceProvider(adyenPSP);
		digitalGetPaymentCard.setPaytCardByPaytServiceProvider(orderModel.getPaymentInfo().getAdyenPaymentCardToken());
		digitalGetPaymentCardList.setDigitalGetPaymentCardRequests(List.of(digitalGetPaymentCard));

		final DigitalPaymentsCardResultList resultModelList = adyenSapDigitalPaymentService.getForPaymentCard(
				digitalGetPaymentCardList, sapDigitalPaymentConfigurationModel);

		final DPAOperationResultModel dpaOperationResultModel = modelService.create(DPAOperationResultModel._TYPECODE);

		final Optional<DigitalPaymentsCardResultModel> resultModel = resultModelList.getDigitalPaymentsCardResultModels().stream().findFirst();

		final StringBuilder dataToDisplay = new StringBuilder();

		if(resultModel.isPresent())
		{
			dataToDisplay.append(String.format(PAYMENT_SERVICE_PROVIDER, resultModel.get().getPaymentServiceProvider()));
			dataToDisplay.append(String.format(DIGITAL_PAYMENT_TRANSACTION, resultModel.get().getPaymentServiceProvider()));
			dataToDisplay.append(String.format(DIGITAL_PAYT_TRANSACTIONRESULT, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult()));
			dataToDisplay.append(String.format(DIGITAL_PAYT_TRANSRSLT_DESC, resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()));
			dataToDisplay.append(String.format(PAYT_CARD_BY_PAYT_SERVICE_PROVIDER, resultModel.get().getPaytCardByPaytServiceProvider()));
		}
		else {
				Messagebox.show("Result model is empty.", "Error Details", new Messagebox.Button[]
						{ Messagebox.Button.OK, Messagebox.Button.CANCEL }, null, null, null);
				return new ActionResult<>(ActionResult.ERROR);
		}

			final StringBuilder rawRequestData = new StringBuilder();
			rawRequestData.append(String.format(RAW_DATA, new Gson().toJson(resultModel)));
			orderModel.getPaymentInfo().setAdyenDPAPaymentCardToken(resultModel.get().getSource() != null ? resultModel.get().getSource().getCard() != null ? resultModel.get().getSource().getCard().getPaytCardByDigitalPaymentSrvc() : "" : "");
			modelService.save(orderModel.getPaymentInfo());
			dpaOperationResultModel.setDpaOperation(DPA_OPERATION);
			dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
			dpaOperationResultModel.setDpaOperationResultDesc(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
			dpaOperationResultModel.setAdyenDPAPaymentCardToken(resultModel.get().getSource().getCard().getPaytCardByDigitalPaymentSrvc());
			dpaOperationResultModel.setDpaOperationPayload(rawRequestData.toString());
			dpaOperationResultModel.setDpaResult(resultModel.get().getDigitalPaymentTransaction().getDigitalPaytTransResult());
			modelService.save(dpaOperationResultModel);
			if(dpaOperationResultModel.getDpaResult().equals("01"))
			{
				Messagebox.show(dataToDisplay.toString(), "Card registered successfully in DPA.", new Messagebox.Button[]
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
	public boolean canPerform(ActionContext<OrderModel> ctx) {
		return true;
	}


}
