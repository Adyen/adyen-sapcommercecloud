package com.adyen.commerce.services;

import com.adyen.model.checkout.PaymentRequest;
import de.hybris.platform.commercefacades.order.data.CartData;

import java.util.Map;

public interface AdyenRequestService {

    String TOTAL_TAX_AMOUNT = "enhancedSchemeData.totalTaxAmount";
    String CUSTOMER_REFERENCE = "enhancedSchemeData.customerReference";
    String FREIGHT_AMOUNT = "enhancedSchemeData.freightAmount";
    String SHIP_FROM_POSTAL_CODE = "enhancedSchemeData.shipFromPostalCode";
    String ORDER_DATE = "enhancedSchemeData.orderDate";
    String DESTINATION_POSTAL_CODE = "enhancedSchemeData.destinationPostalCode";
    String DESTINATION_STATE_PROVINCE_CODE = "enhancedSchemeData.destinationStateProvinceCode";
    String DESTINATION_COUNTRY_CODE = "enhancedSchemeData.destinationCountryCode";
    String DUTY_AMOUNT = "enhancedSchemeData.dutyAmount";

    String ITEM_DETAIL_DESCRIPTION = "enhancedSchemeData.itemDetailLine%d.description";
    String ITEM_DETAIL_PRODUCT_CODE = "enhancedSchemeData.itemDetailLine%d.productCode";
    String ITEM_DETAIL_COMMODITY_CODE = "enhancedSchemeData.itemDetailLine%d.commodityCode";
    String ITEM_DETAIL_QUANTITY = "enhancedSchemeData.itemDetailLine%d.quantity";
    String ITEM_DETAIL_UNIT_OF_MEASURE = "enhancedSchemeData.itemDetailLine%d.unitOfMeasure";
    String ITEM_DETAIL_UNIT_PRICE = "enhancedSchemeData.itemDetailLine%d.unitPrice";
    String ITEM_DETAIL_DISCOUNT_AMOUNT = "enhancedSchemeData.itemDetailLine%d.discountAmount";
    String ITEM_DETAIL_TOTAL_AMOUNT = "enhancedSchemeData.itemDetailLine%d.totalAmount";


    void populateL2L3AdditionalData(final Map<String, String> additionalData, final CartData cartData);


    void applyAdditionalData(CartData cartData, PaymentRequest paymentsRequest);
}
