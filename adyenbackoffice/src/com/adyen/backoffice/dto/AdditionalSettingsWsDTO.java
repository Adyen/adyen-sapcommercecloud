package com.adyen.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdditionalSettingsWsDTO {

    private List<String> includeEventCodes;

    public AdditionalSettingsWsDTO() {
    }

    public List<String> getIncludeEventCodes() {
        return includeEventCodes;
    }

    public void setIncludeEventCodes(List<String> includeEventCodes) {
        this.includeEventCodes = includeEventCodes;
    }
}