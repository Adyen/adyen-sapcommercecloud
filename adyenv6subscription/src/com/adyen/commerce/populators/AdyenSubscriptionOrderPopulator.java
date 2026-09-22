package com.adyen.commerce.populators;

import de.hybris.platform.commercefacades.order.converters.populator.AbstractOrderPopulator;
import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;
import org.apache.commons.lang3.BooleanUtils;

public class AdyenSubscriptionOrderPopulator extends AbstractOrderPopulator<AbstractOrderModel, AbstractOrderData> {
    @Override
    public void populate(AbstractOrderModel orderModel, AbstractOrderData orderData) throws ConversionException {

        orderData.setSubscriptionOrder(BooleanUtils.isTrue(orderModel.getSubscriptionOrder()));
    }
}
