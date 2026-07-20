package com.adyen.commerce.connector.recurly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecurlyAccountCreateRequest
{
    private String code;
    private String email;

    @JsonProperty("billing_info")
    private RecurlyBillingInfoRequest billingInfo;

}