package com.adyen.commerce.connector.recurly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecurlyBillingInfoRequest
{
    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("gateway_code")
    private String gatewayCode;

    @JsonProperty("gateway_attributes")
    private RecurlyGatewayAttributes gatewayAttributes;

    @JsonProperty("payment_gateway_references")
    private List<RecurlyPaymentGatewayReference> paymentGatewayReferences;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGatewayCode() {
        return gatewayCode;
    }

    public void setGatewayCode(String gatewayCode) {
        this.gatewayCode = gatewayCode;
    }

    public RecurlyGatewayAttributes getGatewayAttributes() {
        return gatewayAttributes;
    }

    public void setGatewayAttributes(RecurlyGatewayAttributes gatewayAttributes) {
        this.gatewayAttributes = gatewayAttributes;
    }

    public List<RecurlyPaymentGatewayReference> getPaymentGatewayReferences() {
        return paymentGatewayReferences;
    }

    public void setPaymentGatewayReferences(List<RecurlyPaymentGatewayReference> paymentGatewayReferences) {
        this.paymentGatewayReferences = paymentGatewayReferences;
    }
}
