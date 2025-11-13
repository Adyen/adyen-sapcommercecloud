package com.adyen.v6.converter;

import com.adyen.commerce.data.TokenWebhookRequestData;
import com.adyen.v6.request.TokenizationWebhookRequest;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;
import de.hybris.platform.servicelayer.dto.converter.Converter;

public class TokenizationWebhookRequestConverter implements Converter<TokenizationWebhookRequest, TokenWebhookRequestData> {

    @Override
    public TokenWebhookRequestData convert(TokenizationWebhookRequest tokenizationWebhookRequest) throws ConversionException {
        TokenWebhookRequestData result = new TokenWebhookRequestData();

        return convert(tokenizationWebhookRequest, result);
    }

    @Override
    public TokenWebhookRequestData convert(TokenizationWebhookRequest tokenizationWebhookRequest, TokenWebhookRequestData tokenWebhookRequestData) throws ConversionException {
        tokenWebhookRequestData.setCreatedAt(tokenizationWebhookRequest.getCreatedAt());
        tokenWebhookRequestData.setEventId(tokenizationWebhookRequest.getEventId());
        tokenWebhookRequestData.setEnvironment(tokenizationWebhookRequest.getEnvironment());
        tokenWebhookRequestData.setEventType(tokenizationWebhookRequest.getType());
        tokenWebhookRequestData.setMerchantAccount(tokenizationWebhookRequest.getData().getMerchantAccount());
        tokenWebhookRequestData.setOperation(tokenizationWebhookRequest.getData().getOperation());
        tokenWebhookRequestData.setShopperReference(tokenizationWebhookRequest.getData().getShopperReference());
        tokenWebhookRequestData.setStoredPaymentMethodId(tokenizationWebhookRequest.getData().getStoredPaymentMethodId());
        tokenWebhookRequestData.setPaymentType(tokenizationWebhookRequest.getData().getType());

        return tokenWebhookRequestData;
    }
}
