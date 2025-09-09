package com.adyen.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetCaptureList {

    @JsonProperty("DirectCaptures")
    private List<DigitalPaymentGetCapture> captures;

    public List<DigitalPaymentGetCapture> getCaptures() {
        return captures;
    }

    public void setCaptures(List<DigitalPaymentGetCapture> captures) {
        this.captures = captures;
    }

}
