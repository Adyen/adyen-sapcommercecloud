package com.adyen.commerce.response;

import de.hybris.platform.commercefacades.user.data.RegionData;

import java.util.List;

public class RegionResponse {
    String countryCode;
    List<RegionData> regionData;

    public RegionResponse(){}

    public RegionResponse(String countryCode, List<RegionData> regionData) {
        this.countryCode = countryCode;
        this.regionData = regionData;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public List<RegionData> getRegionData() {
        return regionData;
    }

    public void setRegionData(List<RegionData> regionData) {
        this.regionData = regionData;
    }
}
