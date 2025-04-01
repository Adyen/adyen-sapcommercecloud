/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Hybris Extension
 *
 * Copyright (c) 2019 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 */
package com.adyen.v6.converters;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.nexo.SaleToPOIResponse;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class PosPaymentResponseConverter implements Converter<SaleToPOIResponse, PaymentResponse> {
    @Override
    public PaymentResponse convert(SaleToPOIResponse saleToPOIResponse) {
        String pspReference = null;
        Map<String, String> additionalData = null;
        String additionalResponse = null;

        if (saleToPOIResponse != null && saleToPOIResponse.getPaymentResponse() != null) {
            pspReference = saleToPOIResponse.getPaymentResponse()
                                            .getPaymentResult()
                                            .getPaymentAcquirerData()
                                            .getAcquirerTransactionID()
                                            .getTransactionID();

            additionalResponse = saleToPOIResponse.getPaymentResponse()
                                                  .getResponse()
                                                  .getAdditionalResponse();

        } else if (saleToPOIResponse != null && saleToPOIResponse.getTransactionStatusResponse() != null) {
            pspReference = saleToPOIResponse.getTransactionStatusResponse()
                                            .getRepeatedMessageResponse()
                                            .getRepeatedResponseMessageBody()
                                            .getPaymentResponse()
                                            .getPaymentResult()
                                            .getPaymentAcquirerData()
                                            .getAcquirerTransactionID()
                                            .getTransactionID();

            additionalResponse = saleToPOIResponse.getTransactionStatusResponse()
                                                  .getRepeatedMessageResponse()
                                                  .getRepeatedResponseMessageBody()
                                                  .getPaymentResponse()
                                                  .getResponse()
                                                  .getAdditionalResponse();
        }
        if (additionalResponse != null) {
            additionalData = parseAdditionalResponse(additionalResponse);
        }
        PaymentResponse paymentsResponse = new PaymentResponse();
        paymentsResponse.setPspReference(pspReference);
        paymentsResponse.setAdditionalData(additionalData);

        return paymentsResponse;
    }

    /*
     * Parse base64 encoded additionalResponse and return as a name/value map
     */
    protected Map<String, String> parseAdditionalResponse(String additionalResponse) {
        Map<String, String> additionalData = new HashMap<>();
        if (StringUtils.isNotEmpty(additionalResponse)) {
            String decodedAdditionalResponse = new String(Base64.getDecoder().decode(additionalResponse), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            additionalData = gson.fromJson(decodedAdditionalResponse, PaymentResponse.class).getAdditionalData();
        }
        return additionalData;
    }
}
