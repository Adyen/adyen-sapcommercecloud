package com.adyen.v6.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Test;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.servicelayer.model.ModelService;

@UnitTest
public class DefaultAdyenOrderServicePaymentInfoTest
{
    @Test
    public void storesTokenAndNtidOnCartPaymentInfo()
    {
        final ModelService modelService = mock(ModelService.class);
        final CartModel cart = mock(CartModel.class);
        final PaymentInfoModel paymentInfo = mock(PaymentInfoModel.class);
        final DefaultAdyenOrderService service = new DefaultAdyenOrderService();
        service.setModelService(modelService);
        when(cart.getPaymentInfo()).thenReturn(paymentInfo);

        service.updatePaymentInfo(cart, "scheme", Map.of(
                "tokenization.storedPaymentMethodId", "token-1",
                "networkTxReference", "NTID-42"));

        verify(paymentInfo).setAdyenPaymentMethod("scheme");
        verify(paymentInfo).setAdyenSelectedReference("token-1");
        verify(paymentInfo).setAdyenNetworkTxReference("NTID-42");
        verify(modelService).save(paymentInfo);
    }
}
