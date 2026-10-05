package com.adyen.commerce.facades.impl;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.PaymentResponse;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.session.SessionService;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@UnitTest
public class DefaultAdyenCheckoutFacadeDonationSessionTest {
    @Test
    public void shouldReplaceOldDonationDataWhenNewPaymentIsProcessed() {
        final SessionService sessionService = mock(SessionService.class);
        final TestableCheckoutFacade facade = new TestableCheckoutFacade();
        facade.setSessionService(sessionService);

        facade.store(new PaymentResponse()
                .donationToken("token")
                .pspReference("psp-reference")
                .amount(new Amount().currency("EUR").value(1000L)));

        verify(sessionService).removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN);
        verify(sessionService).removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE);
        verify(sessionService).removeAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_IN_PROGRESS);
        verify(sessionService).setAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_TOKEN, "token");
        verify(sessionService).setAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_PSP_REFERENCE, "psp-reference");
        verify(sessionService).setAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_VALUE, 1000L);
        verify(sessionService).setAttribute(DefaultAdyenCheckoutFacade.SESSION_DONATION_ORIGINAL_AMOUNT_CURRENCY, "EUR");
    }

    private static class TestableCheckoutFacade extends DefaultAdyenCheckoutFacade {
        void store(final PaymentResponse response) {
            storeDonationPaymentData(response);
        }
    }
}
