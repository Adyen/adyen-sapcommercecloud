/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * SAP Digital payment payment service provider field class
 */
public class PaymentCardResult
{


	@JsonProperty("PaymentCardExpirationMonth")
	private String paymentCardExpirationMonth;

	@JsonProperty("PaymentCardExpirationYear")
	private String paymentCardExpirationYear;

	@JsonProperty("PaymentCardHolderName")
	private String paymentCardHolderName;

	@JsonProperty("PaymentCardMaskedNumber")
	private String paymentCardMaskedNumber;

	@JsonProperty("PaymentCardType")
	private String paymentCardType;

	@JsonProperty("PaytCardByDigitalPaymentSrvc")
	private String paytCardByDigitalPaymentSrvc;


	public String getPaymentCardExpirationMonth() {
		return paymentCardExpirationMonth;
	}

	public void setPaymentCardExpirationMonth(String paymentCardExpirationMonth) {
		this.paymentCardExpirationMonth = paymentCardExpirationMonth;
	}

	public String getPaymentCardExpirationYear() {
		return paymentCardExpirationYear;
	}

	public void setPaymentCardExpirationYear(String paymentCardExpirationYear) {
		this.paymentCardExpirationYear = paymentCardExpirationYear;
	}

	public String getPaymentCardHolderName() {
		return paymentCardHolderName;
	}

	public void setPaymentCardHolderName(String paymentCardHolderName) {
		this.paymentCardHolderName = paymentCardHolderName;
	}

	public String getPaymentCardMaskedNumber() {
		return paymentCardMaskedNumber;
	}

	public void setPaymentCardMaskedNumber(String paymentCardMaskedNumber) {
		this.paymentCardMaskedNumber = paymentCardMaskedNumber;
	}

	public String getPaymentCardType() {
		return paymentCardType;
	}

	public void setPaymentCardType(String paymentCardType) {
		this.paymentCardType = paymentCardType;
	}

	public String getPaytCardByDigitalPaymentSrvc() {
		return paytCardByDigitalPaymentSrvc;
	}

	public void setPaytCardByDigitalPaymentSrvc(String paytCardByDigitalPaymentSrvc) {
		this.paytCardByDigitalPaymentSrvc = paytCardByDigitalPaymentSrvc;
	}



}
