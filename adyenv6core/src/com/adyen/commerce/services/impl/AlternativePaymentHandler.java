package com.adyen.commerce.services.impl;

import com.adyen.commerce.util.LocalizationUtil;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.LineItem;
import com.adyen.model.checkout.ShopperName;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.util.TaxValue;
import org.apache.commons.lang3.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

/**
 * Handler for alternative payment methods (Klarna, PayPal, etc.)
 */
public class AlternativePaymentHandler implements PaymentMethodHandler {

    private static final String DELIVERY_COST_KEY= "adyen.lineItem.deliveryCost";

    @Override
    public boolean canHandle(String paymentMethod) {
        return paymentMethod != null && (
            paymentMethod.startsWith(PAYMENT_METHOD_KLARNA) ||
            paymentMethod.startsWith(PAYMENT_METHOD_FACILPAY_PREFIX) ||
            OPENINVOICE_METHODS_API.contains(paymentMethod) ||
            paymentMethod.contains(RATEPAY) ||
            PAYMENT_METHOD_PIX.equals(paymentMethod) ||
            paymentMethod.startsWith(PAYMENT_METHOD_BOLETO) ||
            AFTERPAY.equals(paymentMethod) ||
            PAYBRIGHT.equals(paymentMethod) ||
            PAYMENT_METHOD_PAYPO.equals(paymentMethod)
        );
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData,
                                   RecurringContractMode recurringContractMode,
                                   CustomerModel customerModel, Boolean is3DS2Allowed,
                                   Boolean guestUserTokenizationEnabled) {
        
        String paymentMethod = cartData.getAdyenPaymentMethod();
        
        paymentRequest.setShopperName(createShopperName(cartData.getDeliveryAddress()));

        if (PAYMENT_METHOD_PIX.equals(paymentMethod)) {
            setPixData(paymentRequest, cartData);
        } else if (paymentMethod.startsWith(PAYMENT_METHOD_BOLETO)) {
            setBoletoData(paymentRequest, cartData);
        } else if (isOpenInvoiceMethod(paymentMethod)) {
            setOpenInvoiceData(paymentRequest, cartData);
        }
    }

    protected boolean isOpenInvoiceMethod(String paymentMethod) {
        return paymentMethod.startsWith(PAYMENT_METHOD_KLARNA) ||
               paymentMethod.startsWith(PAYMENT_METHOD_FACILPAY_PREFIX) ||
               OPENINVOICE_METHODS_API.contains(paymentMethod) ||
               paymentMethod.contains(RATEPAY);
    }

    protected ShopperName createShopperName(de.hybris.platform.commercefacades.user.data.AddressData addressData) {
        if (addressData == null) {
            return new ShopperName();
        }
        return new ShopperName()
            .firstName(addressData.getFirstName())
            .lastName(addressData.getLastName());
    }

    protected void setPixData(PaymentRequest paymentRequest, CartData cartData) {
        List<LineItem> invoiceLines = cartData.getEntries().stream()
            .filter(entry -> entry.getQuantity() > 0)
            .map(this::createPixLineItem)
            .collect(Collectors.toList());

        paymentRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());
        paymentRequest.setShopperName(new ShopperName()
            .firstName(cartData.getAdyenFirstName())
            .lastName(cartData.getAdyenLastName()));
        paymentRequest.setLineItems(invoiceLines);
    }

    protected LineItem createPixLineItem(OrderEntryData cartEntry) {
        return new LineItem()
            .amountIncludingTax(cartEntry.getBasePrice().getValue().longValue())
            .id(Optional.ofNullable(cartEntry.getProduct().getName())
                .filter(StringUtils::isNotEmpty)
                .orElse("NA"));
    }

    protected void setBoletoData(PaymentRequest paymentRequest, CartData cartData) {
        paymentRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());
        paymentRequest.setShopperName(new ShopperName()
            .firstName(cartData.getAdyenFirstName())
            .lastName(cartData.getAdyenLastName()));

        // Truncate state codes for Brazil
        if (paymentRequest.getBillingAddress() != null) {
            AddressConverter.truncateStateForBoleto(paymentRequest.getBillingAddress());
        }
        if (paymentRequest.getDeliveryAddress() != null) {
            AddressConverter.truncateStateForBoleto(paymentRequest.getDeliveryAddress());
        }
    }

    protected void setOpenInvoiceData(PaymentRequest paymentRequest, CartData cartData) {
        setDateOfBirth(paymentRequest, cartData);
        setSocialSecurityNumber(paymentRequest, cartData);
        setDeviceFingerprint(paymentRequest, cartData);
        setPaymentMethodSpecificData(paymentRequest, cartData);
        setInvoiceLines(paymentRequest, cartData);
    }

    protected void setDateOfBirth(PaymentRequest paymentRequest, CartData cartData) {
        if (cartData.getAdyenDob() != null) {
            OffsetDateTime offsetDateTime = cartData.getAdyenDob().toInstant()
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
            paymentRequest.setDateOfBirth(offsetDateTime);
        }
    }

    protected void setSocialSecurityNumber(PaymentRequest paymentRequest, CartData cartData) {
        if (StringUtils.isNotEmpty(cartData.getAdyenSocialSecurityNumber())) {
            paymentRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());
        }
    }

    protected void setDeviceFingerprint(PaymentRequest paymentRequest, CartData cartData) {
        if (StringUtils.isNotEmpty(cartData.getAdyenDfValue())) {
            paymentRequest.setDeviceFingerprint(cartData.getAdyenDfValue());
        }
    }

    protected void setPaymentMethodSpecificData(PaymentRequest paymentRequest, CartData cartData) {
        String paymentMethod = cartData.getAdyenPaymentMethod();
        
        if (AFTERPAY.equals(paymentMethod)) {
            paymentRequest.setShopperEmail(cartData.getAdyenShopperEmail());
            paymentRequest.setTelephoneNumber(cartData.getAdyenShopperTelephone());
            paymentRequest.setShopperName(new ShopperName()
                .firstName(cartData.getAdyenFirstName())
                .lastName(cartData.getAdyenLastName()));
        } else if (PAYBRIGHT.equals(paymentMethod)) {
            paymentRequest.setTelephoneNumber(cartData.getAdyenShopperTelephone());
        }
    }

    protected void setInvoiceLines(PaymentRequest paymentRequest, CartData cartData) {
        List<LineItem> invoiceLines = new ArrayList<>();
        String currency = cartData.getTotalPriceWithTax().getCurrencyIso();

        // Add product lines
        for (OrderEntryData entry : cartData.getEntries()) {
            if (entry.getQuantity() > 0L) {
                invoiceLines.add(createProductLineItem(entry, currency, cartData.isNet()));
            }
        }

        // Add delivery costs
        if (cartData.getDeliveryCost() != null) {
            invoiceLines.add(createDeliveryLineItem(cartData, currency));
        }

        paymentRequest.setLineItems(invoiceLines);
    }

    protected LineItem createProductLineItem(OrderEntryData entry, String currency, boolean isNet) {
        LineItem lineItem = new LineItem();
        
        String description = Optional.ofNullable(entry.getProduct().getName())
            .filter(StringUtils::isNotEmpty)
            .orElse("NA");
        lineItem.setDescription(description);

        if (StringUtils.isNotEmpty(entry.getProduct().getCode())) {
            lineItem.setId(entry.getProduct().getCode());
        }

        lineItem.setQuantity(entry.getQuantity());

        Amount itemAmount = AmountUtil.createAmount(entry.getBasePrice().getValue(), currency);
        Double tax = entry.getTaxValues().stream()
            .map(TaxValue::getAppliedValue)
            .reduce(0.0, Double::sum);

        if (tax > 0) {
            tax = tax / entry.getQuantity().intValue();
        }

        if (isNet) {
            lineItem.setAmountExcludingTax(itemAmount.getValue());
            lineItem.setTaxAmount(tax.longValue());
        } else {
            lineItem.setAmountIncludingTax(itemAmount.getValue());
        }

        Double percentage = entry.getTaxValues().stream()
            .map(TaxValue::getValue)
            .reduce(0.0, Double::sum) * 100;
        lineItem.setTaxPercentage(percentage.longValue());

        return lineItem;
    }

    protected LineItem createDeliveryLineItem(CartData cartData, String currency) {
        String localizedDeliveryCostName = LocalizationUtil.getLocalizedStringOrDefault(DELIVERY_COST_KEY, "Delivery Cost");

        LineItem lineItem = new LineItem();
        lineItem.setDescription(localizedDeliveryCostName);
        lineItem.setQuantity(1L);

        Amount deliveryAmount = AmountUtil.createAmount(cartData.getDeliveryCost().getValue(), currency);

        if (cartData.isNet()) {
            lineItem.setAmountExcludingTax(deliveryAmount.getValue());
            lineItem.setTaxAmount(cartData.getTotalTax().getValue().longValue());
        } else {
            lineItem.setAmountIncludingTax(deliveryAmount.getValue());
        }

        Double percentage = cartData.getEntries().stream()
            .findFirst()
            .map(OrderEntryData::getTaxValues)
            .stream()
            .flatMap(Collection::stream)
            .map(TaxValue::getValue)
            .reduce(0.0, Double::sum) * 100;

        lineItem.setTaxPercentage(percentage.longValue());
        return lineItem;
    }
}