package com.adyen.model;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BillingAddress {

    @JsonProperty("AddressLine1")
    private String addressLine1;
    @JsonProperty("AddressLine2")
    private String addressLine2;
    @JsonProperty("AddressLine3")
    private String addressLine3;
    @JsonProperty("CityName")
    private String cityName;
    @JsonProperty("CountryThreeDigitISOCode")
    private String countryThreeDigitISOCode;
    @JsonProperty("PostalCode")
    private String postalCode;
    @JsonProperty("RegionName")
    private String regionName;


    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getAddressLine3() {
        return addressLine3;
    }

    public void setAddressLine3(String addressLine3) {
        this.addressLine3 = addressLine3;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getCountryThreeDigitISOCode() {
        return countryThreeDigitISOCode;
    }

    public void setCountryThreeDigitISOCode(String countryThreeDigitISOCode) {
        this.countryThreeDigitISOCode = countryThreeDigitISOCode;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }
}
