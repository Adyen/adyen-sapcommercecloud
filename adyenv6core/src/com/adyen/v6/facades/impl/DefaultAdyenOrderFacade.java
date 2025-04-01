package com.adyen.v6.facades.impl;

import com.adyen.v6.facades.AdyenOrderFacade;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.exceptions.ModelNotFoundException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class DefaultAdyenOrderFacade implements AdyenOrderFacade {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenOrderFacade.class);
    private static final String ORDER_NOT_FOUND_FOR_USER_AND_BASE_STORE = "Order with guid %s not found for current user in current BaseStore";

    private BaseStoreService baseStoreService;
    private CheckoutCustomerStrategy checkoutCustomerStrategy;
    private CustomerAccountService customerAccountService;
    private UserService userService;

    @Override
    public String getPaymentStatus(final String orderCode, final String sessionGuid) {
        OrderModel orderModel = getOrderModelForCode(orderCode, sessionGuid);

        return getPaymentStatusForOrder(orderModel);
    }

    @Override
    public String getPaymentStatusOCC(final String code) {
        final OrderModel orderModel = getOrderModelForCodeOrGuidOCC(code);

        return getPaymentStatusForOrder(orderModel);
    }

    @Override
    public String getOrderCodeForGUID(final String orderGUID, final String sessionGuid) {
        final BaseStoreModel baseStoreModel = baseStoreService.getCurrentBaseStore();

        if (checkoutCustomerStrategy.isAnonymousCheckout()) {
            OrderModel orderModel = customerAccountService.getGuestOrderForGUID(orderGUID, baseStoreModel);
            if (StringUtils.substringBefore(orderModel.getUser().getUid(), "|")
                    .equals(sessionGuid)) {
                return orderModel.getCode();
            }
        }
        LOG.error("Get order for guid on not anonymous checkout");
        return "";
    }

    protected String getPaymentStatusForOrder(final OrderModel orderModel) {
        List<PaymentTransactionModel> paymentTransactions = orderModel.getPaymentTransactions();
        if (paymentTransactions.isEmpty()) {
            return getMessageFromStatus(TransactionStatus.REVIEW.name());
        }
        return getStatus(paymentTransactions);
    }

    public OrderModel getOrderModelForCodeOCC(String code) {
        BaseStoreModel currentBaseStore = baseStoreService.getCurrentBaseStore();
        final OrderModel orderModel;

        orderModel = customerAccountService.getOrderForCode((CustomerModel) userService.getCurrentUser(), code, currentBaseStore);

        if (orderModel == null) {
            throw new UnknownIdentifierException(String.format(ORDER_NOT_FOUND_FOR_USER_AND_BASE_STORE, code));
        }
        return orderModel;
    }

    protected OrderModel getOrderModelForCodeOrGuidOCC(String code) {
        BaseStoreModel currentBaseStore = baseStoreService.getCurrentBaseStore();
        final OrderModel orderModel;

        if (checkoutCustomerStrategy.isAnonymousCheckout()) {
            orderModel = customerAccountService.getGuestOrderForGUID(code,
                    currentBaseStore);
        } else {
            orderModel = customerAccountService.getOrderForCode((CustomerModel) userService.getCurrentUser(), code, currentBaseStore);
        }

        if (orderModel == null) {
            throw new UnknownIdentifierException(String.format(ORDER_NOT_FOUND_FOR_USER_AND_BASE_STORE, code));
        }
        return orderModel;
    }

    protected OrderModel getOrderModelForCode(final String code, final String sessionGuid) {
        final BaseStoreModel baseStoreModel = baseStoreService.getCurrentBaseStore();

        OrderModel orderModel = null;
        if (checkoutCustomerStrategy.isAnonymousCheckout()) {
            orderModel = customerAccountService.getGuestOrderForGUID(code, baseStoreModel);
            if (!StringUtils.substringBefore(orderModel.getUser().getUid(), "|")
                    .equals(sessionGuid)) {
                orderModel = null;
            }
        } else {
            try {
                orderModel = customerAccountService.getOrderForCode((CustomerModel) userService.getCurrentUser(), code,
                        baseStoreModel);
            } catch (final ModelNotFoundException e) {
                throw new UnknownIdentifierException(String.format(ORDER_NOT_FOUND_FOR_USER_AND_BASE_STORE, code));
            }
        }

        if (orderModel == null) {
            throw new UnknownIdentifierException(String.format(ORDER_NOT_FOUND_FOR_USER_AND_BASE_STORE, code));
        }
        return orderModel;
    }

    protected String getStatus(List<PaymentTransactionModel> paymentTransactions) {
        Optional<PaymentTransactionModel> paymentTransactionModelList = paymentTransactions.stream()
                .max(Comparator.comparing(ItemModel::getCreationtime));

        if (paymentTransactionModelList.isPresent()) {
            Optional<PaymentTransactionEntryModel> paymentTransactionEntryModel = paymentTransactionModelList.get().getEntries().stream()
                    .max(Comparator.comparing(ItemModel::getCreationtime));

            if (paymentTransactionEntryModel.isPresent()) {
                return getMessageFromStatus(paymentTransactionEntryModel.get().getTransactionStatus());
            }
        }

        throw new ModelNotFoundException("No entries in payment transaction model.");
    }

    protected String getMessageFromStatus(String transactionStatus) {
        if (transactionStatus.equals(TransactionStatus.ACCEPTED.name())) {
            return "completed";
        }
        if (transactionStatus.equals(TransactionStatus.REJECTED.name())) {
            return "rejected";
        }
        if (transactionStatus.equals(TransactionStatus.REVIEW.name())) {
            return "waiting";
        }
        if (transactionStatus.equals(TransactionStatus.ERROR.name())) {
            return "error";
        }

        LOG.warn("Unknown transaction status.");
        return "unknown";
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public void setCheckoutCustomerStrategy(CheckoutCustomerStrategy checkoutCustomerStrategy) {
        this.checkoutCustomerStrategy = checkoutCustomerStrategy;
    }

    public void setCustomerAccountService(CustomerAccountService customerAccountService) {
        this.customerAccountService = customerAccountService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
