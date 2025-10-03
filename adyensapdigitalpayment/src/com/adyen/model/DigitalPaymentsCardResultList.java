package com.adyen.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentsCardResultList
{

    @JsonProperty("PaymentCards")
    private List<DigitalPaymentsCardResultModel> digitalPaymentsCardResultModels;

    public List<DigitalPaymentsCardResultModel> getDigitalPaymentsCardResultModels() {
        return digitalPaymentsCardResultModels;
    }

    public void setDigitalPaymentsCardResultModels(List<DigitalPaymentsCardResultModel> digitalPaymentsCardResultModels) {
        this.digitalPaymentsCardResultModels = digitalPaymentsCardResultModels;
    }

}
