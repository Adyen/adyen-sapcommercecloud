package com.adyen.sapdigitalpaymentbackoffice.widgets.actions;

import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.AUTHORIZATION_DOESN_T_EXIST;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RECEIVED_AUTHORIZATION_DETAILS;
import static com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.ActionConstants.RETRIEVE_AUTH_WITH_DPA;

import de.hybris.platform.cissapdigitalpayment.model.SAPDigitalPaymentConfigurationModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.time.Instant;
import java.util.Date;
import javax.annotation.Resource;

import org.assertj.core.util.Lists;
import org.zkoss.zk.ui.Component;

import com.adyen.model.DPAOperationResultModel;
import com.adyen.model.DigitalPaymentGetAuthorizationResult;
import com.adyen.model.DigitalPaymentGetAuthorizationResultList;
import com.adyen.model.authorization.FetchAuthorization;
import com.adyen.model.authorization.FetchAuthorizationList;
import com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils.MessageBoxUtil;
import com.adyen.service.AdyenSapDigitalPaymentService;
import com.google.gson.Gson;
import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;
import com.hybris.cockpitng.engine.impl.AbstractComponentWidgetAdapterAware;
import com.hybris.cockpitng.util.WidgetUtils;

public class RetrieveAuthWithDPA extends AbstractComponentWidgetAdapterAware implements CockpitAction<OrderModel, Object> {

	private static final String AUTHORIZATION_DETAILS = """
			Received following AUTHORIZATION information:
			Authorization Amount: %s
			Authorization Currency: %s
			Authorization Date: %s
			Authorization Expiration Date: %s
			Authorization DPA: %s
			Authorization PSP: %s
			Authorization Status: %s
			DigitalPaymentTransaction ID: %s
			DigitalPaymentTransaction status: %s
			DigitalPaymentTransaction description: %s
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
		ret = orderModel.getPaymentInfo().getAdyenDPAAuthorizationCode() != null;
		return ret;
	}

	@Resource
	private ConfigurationService configurationService;

	@Override
	public ActionResult<Object> perform(final ActionContext<OrderModel> context) {

		final OrderModel orderModel = context.getData();
		final SAPDigitalPaymentConfigurationModel sapDigitalPaymentConfiguration = orderModel.getStore().getSapDigitalPaymentConfiguration();

		final FetchAuthorizationList payload = new FetchAuthorizationList();
		final FetchAuthorization authorization = new FetchAuthorization();
		authorization.setAuthorizationByDigitalPaytSrvc(orderModel.getPaymentInfo().getAdyenDPAAuthorizationCode());
		payload.setFetchAuthorizationList(Lists.newArrayList(authorization));

		final DigitalPaymentGetAuthorizationResultList results = adyenSapDigitalPaymentService.getAuthorization(payload, sapDigitalPaymentConfiguration);

		final StringBuilder dataToDisplay = new StringBuilder();
		final StringBuilder dataToSaveToModel = new StringBuilder();

		if (results != null) {

			for (DigitalPaymentGetAuthorizationResult authorizationResultModel : results.getAuthorizationResults()) {
				dataToSaveToModel.append("Raw data:").append(new Gson().toJson(authorizationResultModel));
				final DPAOperationResultModel dpaOperationResult = buildDPAOperationResultModel(authorizationResultModel, dataToSaveToModel);
				modelService.save(dpaOperationResult);
				createDisplayData(authorizationResultModel, dataToDisplay);
			}
		} else {
			dataToDisplay.append(AUTHORIZATION_DOESN_T_EXIST);
		}
		return MessageBoxUtil.showSuccess(dataToDisplay.toString(), RECEIVED_AUTHORIZATION_DETAILS);
	}

	private static void createDisplayData(final DigitalPaymentGetAuthorizationResult authorizationResultModel, final StringBuilder dataToDisplay) {
		if (authorizationResultModel.getAuthorization() != null) {
			final String formatted = prepareAuthInfo(authorizationResultModel);
			dataToDisplay.append(formatted);
		}
		if (authorizationResultModel.getSource() != null && authorizationResultModel.getSource().getCard() != null) {
			dataToDisplay.append("\n Card DPA Token: ").
					append(authorizationResultModel.getSource().getCard().getPaytCardByDigitalPaymentSrvc());
		}
		if (authorizationResultModel.getSource() != null && authorizationResultModel.getSource().getMerchant() != null) {
			dataToDisplay.append(" \n Merchant Account: ").
					append(authorizationResultModel.getSource().getMerchant().getAccount());
		}
	}

	private static DPAOperationResultModel buildDPAOperationResultModel(final DigitalPaymentGetAuthorizationResult authorizationResultModel, final StringBuilder dataToSaveToModel) {
		final DPAOperationResultModel dpaOperationResult = new DPAOperationResultModel();
		dpaOperationResult.setDpaOperationPayload(dataToSaveToModel.toString());
		dpaOperationResult.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResult.setDpaAuthCode(authorizationResultModel.getAuthorization().getAuthorizationByPaytSrvcPrvdr());
		dpaOperationResult.setDpaOperation(RETRIEVE_AUTH_WITH_DPA);
		dpaOperationResult.setDpaResult(authorizationResultModel.getDigitalPaymentTransaction().getDigitalPaytTransResult());
		dpaOperationResult.setDpaOperationResultDesc(authorizationResultModel.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
		return dpaOperationResult;
	}

	private static String prepareAuthInfo(final DigitalPaymentGetAuthorizationResult authorizationResultModel) {
		var auth = authorizationResultModel.getAuthorization();
		var transaction = authorizationResultModel.getDigitalPaymentTransaction();
		return AUTHORIZATION_DETAILS.formatted(
				String.valueOf(auth.getAuthorizedAmountInAuthznCrcy()),
				String.valueOf(auth.getAuthorizationCurrency()),
				String.valueOf(auth.getAuthorizationDateTime()),
				String.valueOf(auth.getAuthorizationExpirationDateTme()),
				String.valueOf(auth.getAuthorizationByDigitalPaytSrvc()),
				String.valueOf(auth.getAuthorizationByPaytSrvcPrvdr()),
				String.valueOf(auth.getDetailedAuthorizationStatus()),
				String.valueOf(transaction.getDigitalPaymentTransaction()),
				String.valueOf(transaction.getDigitalPaytTransResult()),
				String.valueOf(transaction.getDigitalPaytTransRsltDesc())
		);
	}

	protected Component getRoot() {
		return widgetUtils.getRoot();
	}
}
