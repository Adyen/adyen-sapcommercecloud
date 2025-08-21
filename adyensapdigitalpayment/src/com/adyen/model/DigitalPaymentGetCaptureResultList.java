package com.adyen.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetCaptureResultList
{

    @JsonProperty("DirectCaptures")
    private List<DigitalPaymentGetCaptureResultModel> captures;


    public List<DigitalPaymentGetCaptureResultModel> getCaptures() {
        return captures;
    }

    public void setCaptures(List<DigitalPaymentGetCaptureResultModel> captures) {
        this.captures = captures;
    }
}
