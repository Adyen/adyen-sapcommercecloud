package com.adyen.v6.controllers;

import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenNotificationControllerTest {

    private final AdyenNotificationController controller = new AdyenNotificationController();

    @Test
    public void redactSensitiveData_shouldReturnNull_whenInputIsNull() throws Exception {
        assertNull(invokeRedactSensitiveData(null));
    }

    @Test
    public void redactSensitiveData_shouldReturnEmptyString_whenInputIsEmpty() throws Exception {
        assertEquals("", invokeRedactSensitiveData(""));
    }

    @Test
    public void redactSensitiveData_shouldRedactAdditionalDataObjectAndPreserveFollowingComma() throws Exception {
        String input = "{\"eventCode\":\"AUTHORISATION\",\"additionalData\":{\"cardSummary\":\"1234\",\"issuer\":\"visa\"},\"pspReference\":\"ABC123\"}";

        String result = invokeRedactSensitiveData(input);

        assertEquals("{\"eventCode\":\"AUTHORISATION\",\"additionalData\":{},\"pspReference\":\"ABC123\"}", result);
    }

    @Test
    public void redactSensitiveData_shouldRedactAdditionalDataObjectAtEndOfPayload() throws Exception {
        String input = "{\"eventCode\":\"AUTHORISATION\",\"additionalData\":{\"cardSummary\":\"1234\",\"issuer\":\"visa\"}}";

        String result = invokeRedactSensitiveData(input);

        assertEquals("{\"eventCode\":\"AUTHORISATION\",\"additionalData\":{}}", result);
    }

    @Test
    public void redactSensitiveData_shouldLeavePayloadUntouched_whenNoAdditionalDataIsPresent() throws Exception {
        String input = "{\"eventCode\":\"AUTHORISATION\",\"pspReference\":\"ABC123\"}";

        String result = invokeRedactSensitiveData(input);

        assertEquals(input, result);
    }

    private String invokeRedactSensitiveData(final String requestString) throws Exception {
        Method method = AdyenNotificationController.class.getDeclaredMethod("redactSensitiveData", String.class);
        method.setAccessible(true);
        return (String) method.invoke(controller, requestString);
    }
}
