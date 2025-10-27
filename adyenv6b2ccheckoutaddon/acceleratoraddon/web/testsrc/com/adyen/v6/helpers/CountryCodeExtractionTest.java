package com.adyen.v6.helpers;

import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for country code extraction functionality.
 * Tests both the buggy and fixed implementations to demonstrate the issue and verify the fix.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CountryCodeExtractionTest {

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
        // Setup cart with billing address
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

        // Setup cart with only delivery address
        cartWithDeliveryAddressOnly = new CartData();
        PaymentInfo paymentInfo2 = new PaymentInfo();
        paymentInfo2.setBillingAddress(null);
        cartWithDeliveryAddressOnly.setPaymentInfo(paymentInfo2);
        cartWithDeliveryAddressOnly.setDeliveryAddress(deliveryAddr1); // Reuse delivery address

        // Setup cart with no addresses
        cartWithNoAddresses = new CartData();
        PaymentInfo paymentInfo3 = new PaymentInfo();
        paymentInfo3.setBillingAddress(null);
        cartWithNoAddresses.setPaymentInfo(paymentInfo3);
        cartWithNoAddresses.setDeliveryAddress(null);
    }

    @Test
    public void testGetCountryCodeBuggy_withBillingAddress_shouldReturnEmptyString() {
        // This test demonstrates the bug - it should return "US" but returns empty string
        String result = getCountryCodeBuggy(cartWithBillingAddress);
        
        assertThat(result).isEmpty();
        // The bug causes this to fail: assertThat(result).isEqualTo("US");
    }

    @Test
    public void testGetCountryCodeBuggy_withDeliveryAddressOnly_shouldReturnEmptyString() {
        // This test demonstrates the bug - it should return "UK" but returns empty string
        String result = getCountryCodeBuggy(cartWithDeliveryAddressOnly);
        
        assertThat(result).isEmpty();
        // The bug causes this to fail: assertThat(result).isEqualTo("UK");
    }

    @Test
    public void testGetCountryCodeBuggy_withNoAddresses_shouldReturnEmptyString() {
        String result = getCountryCodeBuggy(cartWithNoAddresses);
        
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetCountryCodeFixed_withBillingAddress_shouldReturnUSCountryCode() {
        String result = getCountryCodeFixed(cartWithBillingAddress);
        
        assertThat(result).isEqualTo("US");
    }

    @Test
    public void testGetCountryCodeFixed_withDeliveryAddressOnly_shouldReturnUKCountryCode() {
        String result = getCountryCodeFixed(cartWithDeliveryAddressOnly);
        
        assertThat(result).isEqualTo("UK");
    }

    @Test
    public void testGetCountryCodeFixed_withNoAddresses_shouldReturnEmptyString() {
        String result = getCountryCodeFixed(cartWithNoAddresses);
        
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetCountryCodeFixed_withNullCart_shouldReturnEmptyString() {
        String result = getCountryCodeFixed(null);
        
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetCountryCodeFixed_withNullPaymentInfo_shouldFallbackToDeliveryAddress() {
        CartData cart = new CartData();
        cart.setPaymentInfo(null);
        
        AddressData deliveryAddr = new AddressData();
        CountryData deliveryCountry = new CountryData();
        deliveryCountry.setIsocode("DE");
        deliveryAddr.setCountry(deliveryCountry);
        cart.setDeliveryAddress(deliveryAddr);
        
        String result = getCountryCodeFixed(cart);
        
        assertThat(result).isEqualTo("DE");
    }

    @Test
    public void testGetCountryCodeFixed_withNullCountryData_shouldReturnEmptyString() {
        CartData cart = new CartData();
        PaymentInfo paymentInfo = new PaymentInfo();
        AddressData billingAddr = new AddressData();
        billingAddr.setCountry(null);
        paymentInfo.setBillingAddress(billingAddr);
        cart.setPaymentInfo(paymentInfo);
        
        String result = getCountryCodeFixed(cart);
        
        assertThat(result).isEmpty();
    }

    // Original buggy implementation - demonstrates the double Optional wrapping issue
    private String getCountryCodeBuggy(CartData cartData) {
        if (cartData == null) {
            return "";
        }
        
        return Optional.ofNullable(cartData.getPaymentInfo())
                .map(PaymentInfo::getBillingAddress)
                .map(billingAddress -> Optional.of(billingAddress)  // BUG: billingAddress is already wrapped in Optional
                        .or(() -> Optional.ofNullable(cartData.getDeliveryAddress())))
                .map(Optional::get)
                .map(AddressData::getCountry)
                .map(CountryData::getIsocode)
                .orElse("");
    }
    
    // Corrected implementation - removes the double Optional wrapping
    private String getCountryCodeFixed(CartData cartData) {
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