package com.adyen.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)

public class WebhookDataWsDTO {

    private String id;
    private String type;
    private String description;
    private String url;
    private Boolean active;
    private String communicationFormat;
    private Boolean hasError;
    private AdditionalSettingsWsDTO additionalSettings;

    public WebhookDataWsDTO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getCommunicationFormat() {
        return communicationFormat;
    }

    public void setCommunicationFormat(String communicationFormat) {
        this.communicationFormat = communicationFormat;
    }

    public Boolean getHasError() {
        return hasError;
    }

    public void setHasError(Boolean hasError) {
        this.hasError = hasError;
    }

    public AdditionalSettingsWsDTO getAdditionalSettings() {
        return additionalSettings;
    }

    public void setAdditionalSettings(AdditionalSettingsWsDTO additionalSettings) {
        this.additionalSettings = additionalSettings;
    }
}