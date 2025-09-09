package com.adyen.model;

import java.util.List;

import com.adyen.model.DigitalGetPaymentCard;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalGetPaymentCardList {

    @JsonProperty("PaymentCards")
    private List<DigitalGetPaymentCard> digitalGetPaymentCardRequests;

    public List<DigitalGetPaymentCard> getDigitalGetPaymentCardRequests() {
        return digitalGetPaymentCardRequests;
    }

    public void setDigitalGetPaymentCardRequests(List<DigitalGetPaymentCard> digitalGetPaymentCardRequests) {
        this.digitalGetPaymentCardRequests = digitalGetPaymentCardRequests;
    }
}
