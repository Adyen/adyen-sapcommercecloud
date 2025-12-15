package com.adyen.v6.backoffice.widgets.actions.cancel;

import com.hybris.cockpitng.actions.ActionContext;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.ordercancel.CancelDecision;
import de.hybris.platform.ordercancel.OrderCancelService;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.user.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCancelOrderActionTest {

    @Mock
    private ActionContext<OrderModel> actionContextMock;
    @Mock
    private OrderModel orderModelMock;
    @Mock
    private AbstractOrderEntryModel orderEntryMock;
    @Mock
    private OrderCancelService orderCancelServiceMock;
    @Mock
    private UserService userServiceMock;
    @Mock
    private UserModel userMock;
    @Mock
    private PaymentTransactionModel paymentTransactionModelMock;
    @Mock
    private CancelDecision cancelDecisionMock;
    private List<OrderStatus> notCancellableOrderStatus = Arrays.asList(OrderStatus.PAYMENT_NOT_VOIDED, OrderStatus.TAX_NOT_VOIDED);

    @InjectMocks
    private AdyenCancelOrderAction testObj = new AdyenCancelOrderAction() {
        //@Override
        protected List<OrderStatus> getNotCancellableOrderStatus() {
            return notCancellableOrderStatus;
        }
    };

    @Before
    public void setUp() {
        when(actionContextMock.getData()).thenReturn(orderModelMock);
        when(orderModelMock.getEntries()).thenReturn(singletonList(orderEntryMock));
        when(userServiceMock.getCurrentUser()).thenReturn(userMock);
        when(orderModelMock.getStatus()).thenReturn(OrderStatus.CREATED);
        when(orderCancelServiceMock.isCancelPossible(orderModelMock, userMock, false, false)).thenReturn(cancelDecisionMock);
        when(cancelDecisionMock.isAllowed()).thenReturn(true);
        when(orderModelMock.getPaymentTransactions()).thenReturn(singletonList(paymentTransactionModelMock));
    }

    @Test
    public void canPerform_WhenOrderNull_ShouldReturnFalse() {
        when(actionContextMock.getData()).thenReturn(null);

        assertFalse(testObj.canPerform(actionContextMock));
    }

    @Test
    public void canPerform_WhenNoEntries_ShouldReturnFalse() {
        when(orderModelMock.getEntries()).thenReturn(emptyList());

        assertFalse(testObj.canPerform(actionContextMock));
    }

    @Test
    public void canPerform_WhenCancelNotPossible_ShouldReturnFalse() {
        when(cancelDecisionMock.isAllowed()).thenReturn(false);

        assertFalse(testObj.canPerform(actionContextMock));
    }

    @Test
    public void canPerform_WhenOrderStatusNotSatisfied_ShouldReturnFalse() {
        when(orderModelMock.getStatus()).thenReturn(OrderStatus.PAYMENT_NOT_VOIDED);

        assertFalse(testObj.canPerform(actionContextMock));
    }

    @Test
    public void canPerform_WhenAllChecksAreTrue_ShouldReturnTrue() {
        assertTrue(testObj.canPerform(actionContextMock));
    }
}
