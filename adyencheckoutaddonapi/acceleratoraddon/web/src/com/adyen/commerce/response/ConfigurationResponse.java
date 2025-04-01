package com.adyen.commerce.response;

import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commercefacades.user.data.TitleData;

import java.util.List;
import java.util.Map;

public class ConfigurationResponse {
    private boolean isAnonymous;
    private List<CountryData> countries;
    private List<TitleData> titles;
    private List<RegionResponse> regions;

    public boolean isAnonymous() {
        return isAnonymous;
    }

    public void setAnonymous(boolean anonymous) {
        isAnonymous = anonymous;
    }

    public List<CountryData> getCountries() {
        return countries;
    }

    public void setCountries(List<CountryData> countries) {
        this.countries = countries;
    }

    public List<TitleData> getTitles() {
        return titles;
    }

    public void setTitles(List<TitleData> titles) {
        this.titles = titles;
    }

    public List<RegionResponse> getRegions() {
        return regions;
    }

    public void setRegions(List<RegionResponse> regions) {
        this.regions = regions;
    }
}
