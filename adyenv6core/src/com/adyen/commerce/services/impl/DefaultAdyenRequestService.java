package com.adyen.commerce.services.impl;

import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.PaymentRequest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang3.StringUtils;
import org.spockframework.util.CollectionUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public class DefaultAdyenRequestService implements AdyenRequestService {


    private BaseStoreService baseStoreService;
    private CartService cartService;
    private ConfigurationService configurationService;

    private static final String L2L3_EDS_SUPPORTED_BRANDS = "adyen.l2l3eds.supported.brands";
    private static final String L2L3_EDS_SUPPORTED_COUNTRIES = "adyen.l2l3eds.supported.countries";

    public DefaultAdyenRequestService(BaseStoreService baseStoreService, CartService cartService, ConfigurationService configurationService) {
        this.baseStoreService = baseStoreService;
        this.cartService = cartService;
        this.configurationService = configurationService;
    }

    @Override
    public void populateL2L3AdditionalData(final Map<String, String> additionalData, final CartData cartData) {
        // required fields
        if (cartData.getTotalTax() != null) {
            additionalData.put(TOTAL_TAX_AMOUNT, String.valueOf(cartData.getTotalTax().getValue()));
        }
        if (StringUtils.isNotEmpty(cartData.getMerchantCustomerId())) {
            additionalData.put(CUSTOMER_REFERENCE, cartData.getMerchantCustomerId());
        }
        // not required but available
        if (cartData.getDeliveryCost() != null) {
            additionalData.put(FREIGHT_AMOUNT, String.valueOf(cartData.getDeliveryCost().getValue()));
        }
        if (cartData.getDeliveryAddress() != null) {
            if (cartData.getDeliveryAddress().getPostalCode() != null) {
                additionalData.put(DESTINATION_POSTAL_CODE, cartData.getDeliveryAddress().getPostalCode());
            }
            if (cartData.getDeliveryAddress().getCountry() != null && cartData.getDeliveryAddress().getCountry().getIsocode() != null) {
                additionalData.put(DESTINATION_COUNTRY_CODE, cartData.getDeliveryAddress().getCountry().getIsocode());
            }
        }

        // Extract item details from cartData and populate additionalData using a stream
        if (cartData.getEntries() != null) {
            cartData.getEntries().forEach(
                    entry -> {
                        if (entry != null && entry.getProduct() != null) {
                            additionalData.put(String.format(ITEM_DETAIL_PRODUCT_CODE, entry.getEntryNumber()),
                                    Optional.ofNullable(entry.getProduct().getCode()).orElse(StringUtils.EMPTY));
                            additionalData.put(String.format(ITEM_DETAIL_DESCRIPTION, entry.getEntryNumber()),
                                    Optional.ofNullable(entry.getProduct().getName()).orElse(StringUtils.EMPTY));

                            additionalData.put(String.format(ITEM_DETAIL_QUANTITY, entry.getEntryNumber()), String.valueOf(entry.getQuantity()));

                            additionalData.put(String.format(ITEM_DETAIL_UNIT_OF_MEASURE, entry.getEntryNumber()),
                                    Optional.ofNullable(entry.getUnitOfMeasure()).orElse(StringUtils.EMPTY));

                            additionalData.put(String.format(ITEM_DETAIL_COMMODITY_CODE, entry.getEntryNumber()),
                                    Optional.ofNullable(entry.getProduct().getCommodityCode()).orElse(StringUtils.EMPTY));

                            if (entry.getTotalPrice() != null) {
                                additionalData.put(String.format(ITEM_DETAIL_TOTAL_AMOUNT, entry.getEntryNumber()),
                                        String.valueOf(Optional.ofNullable(entry.getTotalPrice().getValue()).orElse(BigDecimal.ZERO)));
                            }

                            if (entry.getBasePrice() != null) {
                                additionalData.put(String.format(ITEM_DETAIL_UNIT_PRICE, entry.getEntryNumber()),
                                        String.valueOf(Optional.ofNullable(entry.getBasePrice().getValue()).orElse(BigDecimal.ZERO)));
                            }
                        }
                    }
            );
        }
    }

    @Override
    public void applyAdditionalData(CartData cartData, PaymentRequest paymentsRequest) {
        Map<String, String> additionalData = new HashMap<>();
        CartModel sessionCart = cartService.getSessionCart();
        if(canL23EdsBeSent(paymentsRequest, sessionCart)) {
            populateL2L3AdditionalData(additionalData, cartData);
        }

        paymentsRequest.setAdditionalData(additionalData);

    }

    protected boolean canL23EdsBeSent(PaymentRequest paymentsRequest, CartModel sessionCart) {
        return Optional.ofNullable(baseStoreService.getCurrentBaseStore())
                .map(store -> store.getL2L3ESDEnabled())
                .orElse(false) &&
                Optional.ofNullable(paymentsRequest.getPaymentMethod())
                        .map(method -> method.getActualInstance())
                        .filter(instance -> instance instanceof CardDetails)
                        .map(instance -> (CardDetails) instance)
                        .map(cardDetails -> getL2L3SupportedBrands().contains(cardDetails.getBrand()))
                        .orElse(false) &&
                Optional.ofNullable(sessionCart.getDeliveryAddress())
                        .map(address -> address.getCountry())
                        .map(country -> getL2L3SupportedCountries().contains(country.getIsocode()))
                        .orElse(false);
    }

    protected List<String> getL2L3SupportedBrands() {
        String property = configurationService.getConfiguration().getString(L2L3_EDS_SUPPORTED_BRANDS);
        return property != null ? List.of(property.split(",")) : new ArrayList<>();
    }

    protected List<String> getL2L3SupportedCountries() {
        String property = configurationService.getConfiguration().getString(L2L3_EDS_SUPPORTED_COUNTRIES);
        return property != null ? List.of(property.split(",")) : new ArrayList<>();
    }
}
