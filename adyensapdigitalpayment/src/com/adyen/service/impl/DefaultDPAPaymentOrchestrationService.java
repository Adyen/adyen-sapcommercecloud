package com.adyen.service.impl;

import com.adyen.model.DigitalPaymentGetAuthorizationResultList;
import com.adyen.model.DigitalPaymentGetAuthorizationList;
import com.adyen.model.DigitalPaymentGetAuthorization;
import com.adyen.model.DigitalPaymentGetAuthorizationResult;
import com.adyen.model.DigitalPaymentGetCaptureResultList;
import com.adyen.model.DigitalPaymentGetCaptureList;
import com.adyen.model.DigitalPaymentGetCaptureResultModel;
import com.adyen.model.DigitalPaymentGetCapture;
import com.adyen.model.DigitalGetPaymentCard;
import com.adyen.model.DigitalGetPaymentCardList;
import com.adyen.model.DigitalPaymentsCardResultModel;
import com.adyen.model.DigitalPaymentsCardResultList;
import com.adyen.model.DigitalGetPaymentCardContext;
import com.adyen.model.DPAOperationResultModel;
import com.adyen.model.authorization.FetchAuthorizationList;
import com.adyen.model.authorization.FetchAuthorization;
import com.adyen.service.model.DPACardResult;
import com.adyen.service.model.DPAAuthorizationResult;
import com.adyen.service.model.DPACaptureResult;
import com.adyen.service.DPAPaymentOrchestrationService;
import com.adyen.service.AdyenSapDigitalPaymentService;
import com.google.gson.Gson;
import de.hybris.platform.cissapdigitalpayment.client.model.DigitalPaymentsTransactionModel;
import de.hybris.platform.cissapdigitalpayment.model.SAPDigitalPaymentConfigurationModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.log4j.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.Collections;
import java.util.List;
import java.util.Date;

import static com.adyen.service.utils.ActionConstants.DPA_SUCCESS_RESULT;
import static com.adyen.service.utils.ActionConstants.RAW_DATA;
import static com.adyen.service.utils.ActionConstants.REGISTER_AUTHORIZATION;
import static com.adyen.service.utils.ActionConstants.REGISTER_CAPTURE;
import static com.adyen.service.utils.ActionConstants.REGISTER_CARD;
import static com.adyen.service.utils.ActionConstants.RETRIEVE_AUTH_WITH_DPA;
import static java.lang.String.format;

public class DefaultDPAPaymentOrchestrationService implements DPAPaymentOrchestrationService {

	private static final Logger LOG = Logger.getLogger(DefaultDPAPaymentOrchestrationService.class);
	public static final String PAYMENT_INFO_IS_NULL_FOR_ORDER = "PaymentInfo is null for order ";
	public static final String ADYEN_PSP = "adyen.psp";

	private final AdyenSapDigitalPaymentService adyenSapDigitalPaymentService;

	private final ModelService modelService;

	private final ConfigurationService configurationService;

	public DefaultDPAPaymentOrchestrationService(AdyenSapDigitalPaymentService adyenSapDigitalPaymentService, ModelService modelService, ConfigurationService configurationService) {
		this.adyenSapDigitalPaymentService = adyenSapDigitalPaymentService;
		this.modelService = modelService;
		this.configurationService = configurationService;
	}

	private final Gson gson = new Gson();

	@Override
	public DPACaptureResult registerCapture(final OrderModel order) {
		if (order == null) {
			LOG.error("Order is null in registerCapture");
			return DPACaptureResult.empty();
		}
		if (order.getPaymentInfo() == null) {
			LOG.error(PAYMENT_INFO_IS_NULL_FOR_ORDER + order.getCode());
			return DPACaptureResult.empty();
		}

		final SAPDigitalPaymentConfigurationModel sapConfig =
				order.getStore().getSapDigitalPaymentConfiguration();

		final String adyenPSP =
				configurationService.getConfiguration().getString(ADYEN_PSP);
		final String adyenDirectCaptureType =
				configurationService.getConfiguration().getString("adyen.directCaptureType");

		final Optional<DigitalPaymentGetCaptureList> captureListOpt =
				buildCaptureList(order, adyenPSP, adyenDirectCaptureType);

		if (captureListOpt.isEmpty()) {
			LOG.error("No capture transaction found for order " + order.getCode());
			return DPACaptureResult.empty();
		}

		final DigitalPaymentGetCaptureResultList resultList =
				adyenSapDigitalPaymentService.getForDirectCapture(
						captureListOpt.get(), sapConfig);

		final Optional<DigitalPaymentGetCaptureResultModel> optResult =
				resultList.getCaptures().stream().findFirst();

		if (optResult.isEmpty()) {
			LOG.error("Empty capture result model for order " + order.getCode());
			return DPACaptureResult.empty();
		}

		final DigitalPaymentGetCaptureResultModel result = optResult.get();
		final DigitalPaymentsTransactionModel tx = result.getDigitalPaymentTransaction();

		if (tx == null) {
			LOG.error("DigitalPaymentTransaction is null in capture result for order " + order.getCode());
			return DPACaptureResult.empty();
		}

		order.getPaymentInfo().setAdyenDPAChargeTransactionId(tx.getDigitalPaymentTransaction());
		modelService.save(order.getPaymentInfo());

		final DPAOperationResultModel dpaOperationResultModel =
				buildCaptureResultModel(result);
		modelService.save(dpaOperationResultModel);

		final String dpaResult = dpaOperationResultModel.getDpaResult();
		final boolean success = DPA_SUCCESS_RESULT.equals(dpaResult);
		final String resultDesc = dpaOperationResultModel.getDpaOperationResultDesc();

		if (!success) {
			LOG.error("DPA capture failed for order " + order.getCode()
					+ " : " + resultDesc);
		}

		return new DPACaptureResult(
				true,
				success,
				dpaResult,
				resultDesc,
				result,
				resultList
		);
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

		final DigitalPaymentGetCapture capture =
				buildCapture(order, adyenPSP, directCaptureType, captureEntryOpt.get());

		final DigitalPaymentGetCaptureList captureList = new DigitalPaymentGetCaptureList();
		captureList.setCaptures(Collections.singletonList(capture));
		return Optional.of(captureList);
	}

	private static DigitalPaymentGetCapture buildCapture(
			OrderModel order,
			String adyenPSP,
			String directCaptureType,
			PaymentTransactionEntryModel captureEntry
	) {
		final DigitalPaymentGetCapture capture = new DigitalPaymentGetCapture();
		capture.setMerchantAccount(order.getStore().getAdyenMerchantAccount());
		capture.setDigitalPaymentDirectCaptureType(directCaptureType);
		capture.setPaymentServiceProvider(adyenPSP);
		capture.setPaymentCurrency(order.getCurrency().getIsocode());
		capture.setAmountInPaymentCurrency(order.getTotalPrice().toString());
		capture.setPaymentByPaymentServicePrvdr(captureEntry.getRequestId());
		return capture;
	}

	private DPAOperationResultModel buildCaptureResultModel(DigitalPaymentGetCaptureResultModel result) {
		final String rawRequestData = format(RAW_DATA, gson.toJson(result));

		final DPAOperationResultModel dpaOperationResultModel =
				modelService.create(DPAOperationResultModel._TYPECODE);
		dpaOperationResultModel.setDpaOperation(REGISTER_CAPTURE);
		dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResultModel.setDpaOperationResultDesc(
				result.getDigitalPaymentTransaction() != null
						? result.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()
						: "");
		dpaOperationResultModel.setAdyenDPAChargeTransactionId(
				result.getDigitalPaymentTransaction() != null
						? result.getDigitalPaymentTransaction().getDigitalPaymentTransaction()
						: "");
		dpaOperationResultModel.setDpaResult(
				result.getDigitalPaymentTransaction() != null
						? result.getDigitalPaymentTransaction().getDigitalPaytTransResult()
						: null);
		dpaOperationResultModel.setDpaOperationPayload(rawRequestData);
		return dpaOperationResultModel;
	}

	@Override
	public DPAAuthorizationResult retrieveAuthorization(final OrderModel order) {
		if (order == null) {
			LOG.error("Order is null in retrieveAuthorization");
			return DPAAuthorizationResult.empty();
		}
		if (order.getPaymentInfo() == null) {
			LOG.error(PAYMENT_INFO_IS_NULL_FOR_ORDER + order.getCode());
			return DPAAuthorizationResult.empty();
		}

		final String authCode = order.getPaymentInfo().getAdyenDPAAuthorizationCode();
		if (authCode == null) {
			LOG.error("Missing AdyenDPAAuthorizationCode for order " + order.getCode());
			return DPAAuthorizationResult.empty();
		}

		final SAPDigitalPaymentConfigurationModel sapConfig =
				order.getStore().getSapDigitalPaymentConfiguration();

		final FetchAuthorizationList payload = new FetchAuthorizationList();
		final FetchAuthorization authorization = new FetchAuthorization();
		authorization.setAuthorizationByDigitalPaytSrvc(authCode);
		payload.setFetchAuthorizationList(Collections.singletonList(authorization));

		final DigitalPaymentGetAuthorizationResultList results =
				adyenSapDigitalPaymentService.getAuthorization(payload, sapConfig);

		if (results == null || results.getAuthorizationResults() == null
				|| results.getAuthorizationResults().isEmpty()) {
			LOG.warn("No authorization details returned from DPA for order " + order.getCode());
			return DPAAuthorizationResult.empty();
		}

		DigitalPaymentGetAuthorizationResult firstResult = null;

		for (DigitalPaymentGetAuthorizationResult authorizationResultModel
				: results.getAuthorizationResults()) {

			if (firstResult == null) {
				firstResult = authorizationResultModel;
			}

			final StringBuilder dataToSaveToModel = new StringBuilder();
			dataToSaveToModel.append("Raw data:")
					.append(gson.toJson(authorizationResultModel));

			final DPAOperationResultModel dpaOperationResult =
					buildAuthorizationDpaOperationResultModel(authorizationResultModel, dataToSaveToModel);
			modelService.save(dpaOperationResult);
		}

		boolean success = false;
		String resultDesc = null;
		String resultCode = null;

		if (firstResult != null && firstResult.getDigitalPaymentTransaction() != null) {
			resultCode = firstResult.getDigitalPaymentTransaction().getDigitalPaytTransResult();
			resultDesc = firstResult.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc();
			success = DPA_SUCCESS_RESULT.equals(resultCode);
		}

		return new DPAAuthorizationResult(
				true,
				success,
				resultCode,
				resultDesc,
				firstResult,
				results
		);
	}

	private DPAOperationResultModel buildAuthorizationDpaOperationResultModel(
			final DigitalPaymentGetAuthorizationResult authorizationResultModel,
			final StringBuilder dataToSaveToModel) {

		final DPAOperationResultModel dpaOperationResult =
				modelService.create(DPAOperationResultModel._TYPECODE);
		dpaOperationResult.setDpaOperationPayload(dataToSaveToModel.toString());
		dpaOperationResult.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResult.setDpaAuthCode(
				authorizationResultModel.getAuthorization().getAuthorizationByPaytSrvcPrvdr());
		dpaOperationResult.setDpaOperation(RETRIEVE_AUTH_WITH_DPA);
		dpaOperationResult.setDpaResult(
				authorizationResultModel.getDigitalPaymentTransaction().getDigitalPaytTransResult());
		dpaOperationResult.setDpaOperationResultDesc(
				authorizationResultModel.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
		return dpaOperationResult;
	}

	@Override
	public DPACardResult registerCard(final OrderModel order) {
		if (order == null) {
			LOG.error("Order is null in registerCard");
			return DPACardResult.empty();
		}
		if (order.getPaymentInfo() == null) {
			LOG.error(PAYMENT_INFO_IS_NULL_FOR_ORDER + order.getCode());
			return DPACardResult.empty();
		}

		try {
			final SAPDigitalPaymentConfigurationModel sapConfig =
					order.getStore().getSapDigitalPaymentConfiguration();

			final CustomerModel customer = (CustomerModel) order.getUser();
			final DigitalGetPaymentCardContext context = new DigitalGetPaymentCardContext();
			context.setShopperReference(customer.getCustomerID());

			final String adyenPsp =
					configurationService.getConfiguration().getString(ADYEN_PSP);

			final DigitalGetPaymentCardList request =
					buildPaymentCardList(order, context, adyenPsp);

			final DigitalPaymentsCardResultList resultList =
					adyenSapDigitalPaymentService.getForPaymentCard(request, sapConfig);

			final Optional<DigitalPaymentsCardResultModel> resultOpt =
					resultList.getDigitalPaymentsCardResultModels().stream().findFirst();

			if (resultOpt.isEmpty()) {
				LOG.warn("Empty DigitalPaymentsCardResultModel for order " + order.getCode());
				return DPACardResult.empty();
			}

			final DigitalPaymentsCardResultModel resultModel = resultOpt.get();

			updatePaymentInfoToken(order, resultModel);

			final DPAOperationResultModel dpaOperationResultModel =
					buildCardResultModel(resultModel);
			modelService.save(dpaOperationResultModel);

			final String dpaResult = dpaOperationResultModel.getDpaResult();
			final boolean success = DPA_SUCCESS_RESULT.equals(dpaResult);
			final String resultDesc = dpaOperationResultModel.getDpaOperationResultDesc();

			if (!success) {
				LOG.error("DPA card registration failed for order " + order.getCode()
						+ " : " + resultDesc);
			}

			return new DPACardResult(
					true,
					success,
					dpaResult,
					resultDesc,
					resultModel,
					resultList
			);
		} catch (Exception e) {
			LOG.error("Exception during DPA card registration for order " + order.getCode(), e);
			return DPACardResult.empty();
		}
	}

	private static DigitalGetPaymentCardList buildPaymentCardList(final OrderModel orderModel,
	                                                              final DigitalGetPaymentCardContext context,
	                                                              final String adyenPSP) {
		final DigitalGetPaymentCardList digitalGetPaymentCardList = new DigitalGetPaymentCardList();
		final DigitalGetPaymentCard digitalGetPaymentCard = new DigitalGetPaymentCard();
		digitalGetPaymentCard.setMerchantAccount(orderModel.getStore().getAdyenMerchantAccount());
		digitalGetPaymentCard.setPaymentCardContext(context);
		digitalGetPaymentCard.setPaymentServiceProvider(adyenPSP);
		digitalGetPaymentCard.setPaytCardByPaytServiceProvider(
				orderModel.getPaymentInfo().getAdyenPaymentCardToken());
		digitalGetPaymentCardList.setDigitalGetPaymentCardRequests(
				Collections.singletonList(digitalGetPaymentCard));
		return digitalGetPaymentCardList;
	}

	private void updatePaymentInfoToken(final OrderModel orderModel,
	                                    final DigitalPaymentsCardResultModel resultModel) {
		final String token =
				resultModel.getSource() != null
						&& resultModel.getSource().getCard() != null
						? resultModel.getSource().getCard().getPaytCardByDigitalPaymentSrvc()
						: "";
		orderModel.getPaymentInfo().setAdyenDPAPaymentCardToken(token);
		modelService.save(orderModel.getPaymentInfo());
	}

	private DPAOperationResultModel buildCardResultModel(final DigitalPaymentsCardResultModel resultModel) {
		final DPAOperationResultModel dpaOperationResultModel =
				modelService.create(DPAOperationResultModel._TYPECODE);

		final String rawRequestData = format(RAW_DATA, gson.toJson(resultModel));

		dpaOperationResultModel.setDpaOperation(REGISTER_CARD);
		dpaOperationResultModel.setDpaOperationTime(Date.from(Instant.now()));
		dpaOperationResultModel.setDpaOperationResultDesc(
				resultModel.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc());
		dpaOperationResultModel.setAdyenDPAPaymentCardToken(
				resultModel.getSource() != null
						&& resultModel.getSource().getCard() != null
						? resultModel.getSource().getCard().getPaytCardByDigitalPaymentSrvc()
						: "");
		dpaOperationResultModel.setDpaOperationPayload(rawRequestData);
		dpaOperationResultModel.setDpaResult(
				resultModel.getDigitalPaymentTransaction().getDigitalPaytTransResult());
		return dpaOperationResultModel;
	}

	@Override
	public DPAAuthorizationResult registerAuthorization(final OrderModel order) {
		if (order == null) {
			LOG.error("Order is null in registerAuthorization");
			return DPAAuthorizationResult.empty();
		}
		if (order.getPaymentInfo() == null) {
			LOG.error(PAYMENT_INFO_IS_NULL_FOR_ORDER + order.getCode());
			return DPAAuthorizationResult.empty();
		}

		final SAPDigitalPaymentConfigurationModel sapConfig =
				order.getStore().getSapDigitalPaymentConfiguration();

		final String adyenPSP =
				configurationService.getConfiguration().getString(ADYEN_PSP);
		final String adyenAuthorizationType =
				configurationService.getConfiguration().getString("adyen.authorizationType");

		final Optional<PaymentTransactionEntryModel> authEntryOpt =
				getAuthorizationPayment(order.getPaymentTransactions());

		if (authEntryOpt.isEmpty()) {
			LOG.error("No authorization transaction found for order " + order.getCode());
			return DPAAuthorizationResult.empty();
		}

		final DigitalPaymentGetAuthorizationList request =
				buildAuthorizationList(order, adyenAuthorizationType, adyenPSP, authEntryOpt.get());

		final DigitalPaymentGetAuthorizationResultList resultList =
				adyenSapDigitalPaymentService.getForPaymentAuthorization(request, sapConfig);

		if (resultList == null || resultList.getAuthorizationResults() == null
				|| resultList.getAuthorizationResults().isEmpty()) {
			LOG.error("Empty authorization result list for order " + order.getCode());
			return DPAAuthorizationResult.empty();
		}

		final DigitalPaymentGetAuthorizationResult firstResult =
				resultList.getAuthorizationResults().stream().findFirst().orElse(null);

		if (firstResult == null || firstResult.getDigitalPaymentTransaction() == null) {
			LOG.error("First authorization result/tx is null for order " + order.getCode());
			return DPAAuthorizationResult.empty();
		}

		if (firstResult.getAuthorization() != null) {
			order.getPaymentInfo().setAdyenDPAAuthorizationCode(
					firstResult.getAuthorization().getAuthorizationByDigitalPaytSrvc()
			);
			modelService.save(order.getPaymentInfo());
		}

		final DPAOperationResultModel op = buildRegisterAuthorizationOperationResult(firstResult);
		modelService.save(op);

		final String resultCode = op.getDpaResult();
		final String resultDesc = op.getDpaOperationResultDesc();
		final boolean success = DPA_SUCCESS_RESULT.equals(resultCode);

		if (!success) {
			LOG.error("DPA authorization registration failed for order "
					+ order.getCode() + " : " + resultDesc);
		}

		return new DPAAuthorizationResult(
				true,
				success,
				resultCode,
				resultDesc,
				firstResult,
				resultList
		);
	}

	private Optional<PaymentTransactionEntryModel> getAuthorizationPayment(List<PaymentTransactionModel> paymentTransactions) {
		return paymentTransactions.stream()
				.flatMap(tx -> tx.getEntries().stream())
				.filter(entry -> PaymentTransactionType.AUTHORIZATION.equals(entry.getType()))
				.findFirst();
	}

	private DigitalPaymentGetAuthorizationList buildAuthorizationList(
			final OrderModel order,
			final String adyenAuthorizationType,
			final String adyenPSP,
			final PaymentTransactionEntryModel authEntry
	) {
		final DigitalPaymentGetAuthorization authorization = new DigitalPaymentGetAuthorization();
		authorization.setAuthorizationByPaytSrvcPrvdr(authEntry.getRequestId());
		authorization.setAuthorizationCurrency(order.getCurrency().getIsocode());
		authorization.setAuthorizedAmountInAuthznCrcy(order.getTotalPrice().toString());
		authorization.setDigitalPaymentAuthorizationType(adyenAuthorizationType);
		authorization.setMerchantAccount(order.getStore().getAdyenMerchantAccount());
		authorization.setPaymentServiceProvider(adyenPSP);

		final DigitalPaymentGetAuthorizationList list = new DigitalPaymentGetAuthorizationList();
		list.setAuthorizations(Collections.singletonList(authorization));
		return list;
	}

	private DPAOperationResultModel buildRegisterAuthorizationOperationResult(
			final DigitalPaymentGetAuthorizationResult result
	) {
		final String rawRequestData = String.format(RAW_DATA, gson.toJson(result));

		final DPAOperationResultModel model = modelService.create(DPAOperationResultModel._TYPECODE);
		model.setDpaOperation(REGISTER_AUTHORIZATION);
		model.setDpaOperationTime(Date.from(Instant.now()));

		model.setDpaAuthCode(
				result.getAuthorization() != null
						? result.getAuthorization().getAuthorizationByPaytSrvcPrvdr()
						: null
		);

		model.setDpaResult(
				result.getDigitalPaymentTransaction() != null
						? result.getDigitalPaymentTransaction().getDigitalPaytTransResult()
						: null
		);

		model.setDpaOperationResultDesc(
				result.getDigitalPaymentTransaction() != null
						? result.getDigitalPaymentTransaction().getDigitalPaytTransRsltDesc()
						: ""
		);

		model.setAdyenDPAAuthorizationCode(
				result.getAuthorization() != null
						? result.getAuthorization().getAuthorizationByDigitalPaytSrvc()
						: ""
		);

		model.setDpaOperationPayload(rawRequestData);
		return model;
	}

}
