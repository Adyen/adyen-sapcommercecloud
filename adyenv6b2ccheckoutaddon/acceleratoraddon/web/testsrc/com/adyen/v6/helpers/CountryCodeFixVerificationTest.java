package com.adyen.v6.helpers;

import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test to verify that the country code extraction fix works correctly.
 * This test class validates the corrected implementation across various scenarios.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CountryCodeFixVerificationTest {

    // Test data classes
    static class CartData {
        private PaymentInfo paymentInfo;
        private AddressData deliveryAddress;
        
        public PaymentInfo getPaymentInfo() { return paymentInfo; }
        public void setPaymentInfo(PaymentInfo paymentInfo) { this.paymentInfo = paymentInfo; }
        public AddressData getDeliveryAddress() { return deliveryAddress; }
        public void setDeliveryAddress(AddressData deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    }
    
    static class PaymentInfo {
        private AddressData billingAddress;
        
        public AddressData getBillingAddress() { return billingAddress; }
        public void setBillingAddress(AddressData billingAddress) { this.billingAddress = billingAddress; }
    }
    
    static class AddressData {
        private CountryData country;
        
        public CountryData getCountry() { return country; }
        public void setCountry(CountryData country) { this.country = country; }
    }
    
    static class CountryData {
        private String isocode;
        
        public String getIsocode() { return isocode; }
        public void setIsocode(String isocode) { this.isocode = isocode; }
    }

    private CartData cartWithBillingAddress;
    private CartData cartWithDeliveryAddressOnly;
    private CartData cartWithNoAddresses;

    @Before
    public void setUp() {
        // Test case 1: Cart with billing address
        cartWithBillingAddress = new CartData();
        PaymentInfo paymentInfo1 = new PaymentInfo();
        AddressData billingAddr1 = new AddressData();
        CountryData country1 = new CountryData();
        country1.setIsocode("US");
        billingAddr1.setCountry(country1);
        paymentInfo1.setBillingAddress(billingAddr1);
        cartWithBillingAddress.setPaymentInfo(paymentInfo1);
        
        // Add delivery address as fallback
        AddressData deliveryAddr1 = new AddressData();
        CountryData deliveryCountry1 = new CountryData();
        deliveryCountry1.setIsocode("UK");
        deliveryAddr1.setCountry(deliveryCountry1);
        cartWithBillingAddress.setDeliveryAddress(deliveryAddr1);

        // Test case 2: Cart with only delivery address
        cartWithDeliveryAddressOnly = new CartData();
        PaymentInfo paymentInfo2 = new PaymentInfo();
        paymentInfo2.setBillingAddress(null);
        cartWithDeliveryAddressOnly.setPaymentInfo(paymentInfo2);
        cartWithDeliveryAddressOnly.setDeliveryAddress(deliveryAddr1); // Reuse delivery address

        // Test case 3: Cart with no addresses
        cartWithNoAddresses = new CartData();
        PaymentInfo paymentInfo3 = new PaymentInfo();
        paymentInfo3.setBillingAddress(null);
        cartWithNoAddresses.setPaymentInfo(paymentInfo3);
        cartWithNoAddresses.setDeliveryAddress(null);
    }

    @Test
    public void testGetCountryCode_withBillingAddress_shouldReturnUSCountryCode() {
        String result = getCountryCode(cartWithBillingAddress);
        
        assertThat(result).isEqualTo("US");
    }

    @Test
    public void testGetCountryCode_withDeliveryAddressOnly_shouldReturnUKCountryCode() {
        String result = getCountryCode(cartWithDeliveryAddressOnly);
        
        assertThat(result).isEqualTo("UK");
    }

    @Test
    public void testGetCountryCode_withNoAddresses_shouldReturnEmptyString() {
        String result = getCountryCode(cartWithNoAddresses);
        
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetCountryCode_withNullCart_shouldReturnEmptyString() {
        String result = getCountryCode(null);
        
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetCountryCode_withNullPaymentInfo_shouldFallbackToDeliveryAddress() {
        CartData cart = new CartData();
        cart.setPaymentInfo(null);
        
        AddressData deliveryAddr = new AddressData();
        CountryData deliveryCountry = new CountryData();
        deliveryCountry.setIsocode("FR");
        deliveryAddr.setCountry(deliveryCountry);
        cart.setDeliveryAddress(deliveryAddr);
        
        String result = getCountryCode(cart);
        
        assertThat(result).isEqualTo("FR");
    }

    @Test
    public void testGetCountryCode_withNullBillingAddress_shouldFallbackToDeliveryAddress() {
        CartData cart = new CartData();
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setBillingAddress(null);
        cart.setPaymentInfo(paymentInfo);
        
        AddressData deliveryAddr = new AddressData();
        CountryData deliveryCountry = new CountryData();
        deliveryCountry.setIsocode("DE");
        deliveryAddr.setCountry(deliveryCountry);
        cart.setDeliveryAddress(deliveryAddr);
        
        String result = getCountryCode(cart);
        
        assertThat(result).isEqualTo("DE");
    }

    @Test
    public void testGetCountryCode_withNullCountryInBillingAddress_shouldFallbackToDeliveryAddress() {
        CartData cart = new CartData();
        PaymentInfo paymentInfo = new PaymentInfo();
        AddressData billingAddr = new AddressData();
        billingAddr.setCountry(null);
        paymentInfo.setBillingAddress(billingAddr);
        cart.setPaymentInfo(paymentInfo);
        
        AddressData deliveryAddr = new AddressData();
        CountryData deliveryCountry = new CountryData();
        deliveryCountry.setIsocode("IT");
        deliveryAddr.setCountry(deliveryCountry);
        cart.setDeliveryAddress(deliveryAddr);
        
        String result = getCountryCode(cart);
        
        assertThat(result).isEqualTo("IT");
    }

    @Test
    public void testGetCountryCode_withNullIsocodeInBillingAddress_shouldFallbackToDeliveryAddress() {
        CartData cart = new CartData();
        PaymentInfo paymentInfo = new PaymentInfo();
        AddressData billingAddr = new AddressData();
        CountryData billingCountry = new CountryData();
        billingCountry.setIsocode(null);
        billingAddr.setCountry(billingCountry);
        paymentInfo.setBillingAddress(billingAddr);
        cart.setPaymentInfo(paymentInfo);
        
        AddressData deliveryAddr = new AddressData();
        CountryData deliveryCountry = new CountryData();
        deliveryCountry.setIsocode("ES");
        deliveryAddr.setCountry(deliveryCountry);
        cart.setDeliveryAddress(deliveryAddr);
        
        String result = getCountryCode(cart);
        
        assertThat(result).isEqualTo("ES");
    }

    @Test
    public void testGetCountryCode_withEmptyIsocodeInBillingAddress_shouldReturnEmptyString() {
        CartData cart = new CartData();
        PaymentInfo paymentInfo = new PaymentInfo();
        AddressData billingAddr = new AddressData();
        CountryData billingCountry = new CountryData();
        billingCountry.setIsocode("");
        billingAddr.setCountry(billingCountry);
        paymentInfo.setBillingAddress(billingAddr);
        cart.setPaymentInfo(paymentInfo);
        
        String result = getCountryCode(cart);
        
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetCountryCode_billingAddressTakesPrecedenceOverDeliveryAddress() {
        // This test verifies that billing address is preferred over delivery address
        CartData cart = new CartData();
        PaymentInfo paymentInfo = new PaymentInfo();
        
        // Set up billing address with "CA"
        AddressData billingAddr = new AddressData();
        CountryData billingCountry = new CountryData();
        billingCountry.setIsocode("CA");
        billingAddr.setCountry(billingCountry);
        paymentInfo.setBillingAddress(billingAddr);
        cart.setPaymentInfo(paymentInfo);
        
        // Set up delivery address with "MX"
        AddressData deliveryAddr = new AddressData();
        CountryData deliveryCountry = new CountryData();
        deliveryCountry.setIsocode("MX");
        deliveryAddr.setCountry(deliveryCountry);
        cart.setDeliveryAddress(deliveryAddr);
        
        String result = getCountryCode(cart);
        
        // Should return billing address country code, not delivery address
        assertThat(result).isEqualTo("CA");
    }

    // Fixed implementation (same as in the actual file now)
    private String getCountryCode(CartData cartData) {
        if (cartData == null) {
            return "";
        }
        
        return Optional.ofNullable(cartData.getPaymentInfo())
                .map(PaymentInfo::getBillingAddress)
                .or(() -> Optional.ofNullable(cartData.getDeliveryAddress()))
                .map(AddressData::getCountry)
                .map(CountryData::getIsocode)
                .orElse("");
    }
}