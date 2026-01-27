package com.adyen.commerce.hooks;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.servicelayer.internal.model.impl.ItemModelCloneCreator;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.mockito.Mockito.*;

public class AdyenRecurringOrdersCloneAbstractOrderHookTest {

    @Mock
    private ItemModelCloneCreator itemModelCloneCreator;

    @Mock
    private ModelService modelService;

    @InjectMocks
    private AdyenRecurringOrdersCloneAbstractOrderHook adyenRecurringOrdersCloneAbstractOrderHook;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAfterClone() {
        // Create mock objects
        AbstractOrderModel original = mock(AbstractOrderModel.class);
        AbstractOrderModel clone = mock(AbstractOrderModel.class);
        AddressModel deliveryAddress = mock(AddressModel.class);
        AddressModel paymentAddress = mock(AddressModel.class);
        PaymentInfoModel paymentInfo = mock(PaymentInfoModel.class);
        OrderEntryModel orderEntry = mock(OrderEntryModel.class);

        // Setup mock behavior
        when(original.getDeliveryAddress()).thenReturn(deliveryAddress);
        when(original.getPaymentAddress()).thenReturn(paymentAddress);
        when(original.getPaymentInfo()).thenReturn(paymentInfo);
        when(original.getEntries()).thenReturn(Collections.singletonList(orderEntry));
        when(clone.getEntries()).thenReturn(Collections.singletonList(orderEntry));
        when(itemModelCloneCreator.copy(deliveryAddress)).thenReturn(deliveryAddress);
        when(itemModelCloneCreator.copy(paymentAddress)).thenReturn(paymentAddress);
        when(itemModelCloneCreator.copy(paymentInfo)).thenReturn(paymentInfo);

        // Call the method to be tested
        adyenRecurringOrdersCloneAbstractOrderHook.afterClone(original, clone, AbstractOrderModel.class);

        // Verify interactions
        verify(clone).setSubscriptionOrder(Boolean.TRUE);
        verify(clone).setDeliveryAddress(any());
        verify(clone).setPaymentAddress(any());
        verify(clone).setPaymentInfo(any());
        verify(modelService, times(4)).save(any());  // Three individual saves and one saveAll
    }

}
