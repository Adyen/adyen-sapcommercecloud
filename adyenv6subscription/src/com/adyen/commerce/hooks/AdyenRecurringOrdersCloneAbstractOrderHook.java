package com.adyen.commerce.hooks;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.order.strategies.ordercloning.CloneAbstractOrderHook;
import de.hybris.platform.servicelayer.internal.model.impl.ItemModelCloneCreator;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.collections4.CollectionUtils

import java.util.List;
import java.util.Optional;

public class AdyenRecurringOrdersCloneAbstractOrderHook implements CloneAbstractOrderHook {

    private ItemModelCloneCreator itemModelCloneCreator;
    private ModelService modelService;

    @Override
    public void beforeClone(AbstractOrderModel original, Class abstractOrderClassResult) {
        // Nothing to do
    }

    @Override
    public <T extends AbstractOrderModel> void afterClone(AbstractOrderModel original, T clone, Class abstractOrderClassResult) {
        copyAddress(original.getDeliveryAddress(), clone::setDeliveryAddress);
        copyAddress(original.getPaymentAddress(), clone::setPaymentAddress);
        copyPaymentInfo(original.getPaymentInfo(), clone::setPaymentInfo);
        clone.setSubscriptionOrder(Boolean.TRUE);
        if (CollectionUtils.isNotEmpty(clone.getEntries())) {
            clone.getEntries().forEach(e -> processEntries(original, (OrderEntryModel) e));
        }
        modelService.saveAll(clone);
    }
    private void copyAddress(AddressModel originalAddress, java.util.function.Consumer<AddressModel> addressSetter) {
        Optional.ofNullable(originalAddress).ifPresent(address -> {
            final AddressModel copiedAddress = (AddressModel) itemModelCloneCreator.copy(address);
            copiedAddress.setDuplicate(Boolean.TRUE);
            addressSetter.accept(copiedAddress);
            modelService.save(copiedAddress);
        });
    }

    private void copyPaymentInfo(PaymentInfoModel originalPaymentInfo, java.util.function.Consumer<PaymentInfoModel> paymentInfoSetter) {
        Optional.ofNullable(originalPaymentInfo).ifPresent(paymentInfo -> {
            final PaymentInfoModel copiedPaymentInfo = (PaymentInfoModel) itemModelCloneCreator.copy(paymentInfo);
            copiedPaymentInfo.setDuplicate(Boolean.TRUE);
            paymentInfoSetter.accept(copiedPaymentInfo);
            modelService.save(copiedPaymentInfo);
        });
    }

    private void processEntries(AbstractOrderModel original, OrderEntryModel entry) {
        entry.setOriginalOrderEntry(
                (OrderEntryModel) original.getEntries().stream()
                        .filter(e2 -> entry.getEntryNumber().equals(e2.getEntryNumber()))
                        .findAny()
                        .orElse(null));
        entry.setMasterEntry(null);
        entry.setCalculated(Boolean.TRUE);
        entry.setIsSubscriptionEntry(Boolean.TRUE);
        modelService.save(entry);
    }


    @Override
    public void beforeCloneEntries(AbstractOrderModel original) {
        // Nothing to do
    }

    @Override
    public <T extends AbstractOrderEntryModel> void afterCloneEntries(AbstractOrderModel original, List<T> clonedEntries) {
        // Nothing to do
    }

    public void setItemModelCloneCreator(ItemModelCloneCreator itemModelCloneCreator) {
        this.itemModelCloneCreator = itemModelCloneCreator;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }
}
