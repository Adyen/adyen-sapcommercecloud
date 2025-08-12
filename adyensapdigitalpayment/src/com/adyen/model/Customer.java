package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Customer {

    @JsonProperty("CustomerAccountNumber")
    private String customerAccountNumber;
    @JsonProperty("EmailAddress")
    private String emailAddress;
    @JsonProperty("FirstName")
    private String firstName;
    @JsonProperty("LastName")
    private String lastName;
    @JsonProperty("OrganizationName")
    private String organizationName;
    @JsonProperty("BusinessPartnerByConsumerAppl")
    private String businessPartnerByConsumerAppl;

    public String getCustomerAccountNumber() {
        return customerAccountNumber;
    }

    public void setCustomerAccountNumber(String customerAccountNumber) {
        this.customerAccountNumber = customerAccountNumber;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

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

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getBusinessPartnerByConsumerAppl() {
        return businessPartnerByConsumerAppl;
    }

    public void setBusinessPartnerByConsumerAppl(String businessPartnerByConsumerAppl) {
        this.businessPartnerByConsumerAppl = businessPartnerByConsumerAppl;
    }

    ;
}
