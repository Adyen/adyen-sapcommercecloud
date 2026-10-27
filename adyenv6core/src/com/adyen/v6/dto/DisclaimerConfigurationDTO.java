package com.adyen.v6.dto;

public class DisclaimerConfigurationDTO {
    private String disclaimerMessage;
    private String disclaimerLink;

    public String getDisclaimerMessage() {
        return disclaimerMessage;
    }

    public void setDisclaimerMessage(final String disclaimerMessage) {
        this.disclaimerMessage = disclaimerMessage;
    }

    public String getDisclaimerLink() {
        return disclaimerLink;
    }

    public void setDisclaimerLink(final String disclaimerLink) {
        this.disclaimerLink = disclaimerLink;
    }
}
