/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.occ.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One subscription row. No plan code, amounts or platform identifiers are exposed. {@code state} is the enum name, {@code tone} is normal, attention or quiet, and the dates are ISO-8601.
 */
public class AdyenSubscriptionEntryWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String code;

	private String productName;

	private String state;

	private String tone;

	private String effectiveDate;

	private Integer quantity;

	private String orderCode;

	private String orderDate;

	private String paymentMethodSummary;

	private boolean manageable;

	private boolean cancellable;

	private boolean paymentMethodChangeable;

	private boolean paymentMethodControl;

	private boolean paymentMethodShareable;

	private boolean paymentMethodChangeCovered;

	private List<AdyenSubscriptionPaymentMethodOptionWsDTO> paymentMethodOptions = new ArrayList<>();

	private List<AdyenSubscriptionPaymentMethodOptionWsDTO> adyenVaultOptions = new ArrayList<>();

	public String getCode()
	{
		return code;
	}

	public void setCode(final String code)
	{
		this.code = code;
	}

	public String getProductName()
	{
		return productName;
	}

	public void setProductName(final String productName)
	{
		this.productName = productName;
	}

	public String getState()
	{
		return state;
	}

	public void setState(final String state)
	{
		this.state = state;
	}

	public String getTone()
	{
		return tone;
	}

	public void setTone(final String tone)
	{
		this.tone = tone;
	}

	public String getEffectiveDate()
	{
		return effectiveDate;
	}

	public void setEffectiveDate(final String effectiveDate)
	{
		this.effectiveDate = effectiveDate;
	}

	public Integer getQuantity()
	{
		return quantity;
	}

	public void setQuantity(final Integer quantity)
	{
		this.quantity = quantity;
	}

	public String getOrderCode()
	{
		return orderCode;
	}

	public void setOrderCode(final String orderCode)
	{
		this.orderCode = orderCode;
	}

	public String getOrderDate()
	{
		return orderDate;
	}

	public void setOrderDate(final String orderDate)
	{
		this.orderDate = orderDate;
	}

	public String getPaymentMethodSummary()
	{
		return paymentMethodSummary;
	}

	public void setPaymentMethodSummary(final String paymentMethodSummary)
	{
		this.paymentMethodSummary = paymentMethodSummary;
	}

	public boolean isManageable()
	{
		return manageable;
	}

	public void setManageable(final boolean manageable)
	{
		this.manageable = manageable;
	}

	public boolean isCancellable()
	{
		return cancellable;
	}

	public void setCancellable(final boolean cancellable)
	{
		this.cancellable = cancellable;
	}

	public boolean isPaymentMethodChangeable()
	{
		return paymentMethodChangeable;
	}

	public void setPaymentMethodChangeable(final boolean paymentMethodChangeable)
	{
		this.paymentMethodChangeable = paymentMethodChangeable;
	}

	public boolean isPaymentMethodControl()
	{
		return paymentMethodControl;
	}

	public void setPaymentMethodControl(final boolean paymentMethodControl)
	{
		this.paymentMethodControl = paymentMethodControl;
	}

	public boolean isPaymentMethodShareable()
	{
		return paymentMethodShareable;
	}

	public void setPaymentMethodShareable(final boolean paymentMethodShareable)
	{
		this.paymentMethodShareable = paymentMethodShareable;
	}

	public boolean isPaymentMethodChangeCovered()
	{
		return paymentMethodChangeCovered;
	}

	public void setPaymentMethodChangeCovered(final boolean paymentMethodChangeCovered)
	{
		this.paymentMethodChangeCovered = paymentMethodChangeCovered;
	}

	public List<AdyenSubscriptionPaymentMethodOptionWsDTO> getPaymentMethodOptions()
	{
		return paymentMethodOptions;
	}

	public void setPaymentMethodOptions(final List<AdyenSubscriptionPaymentMethodOptionWsDTO> paymentMethodOptions)
	{
		this.paymentMethodOptions = paymentMethodOptions == null ? new ArrayList<>() : paymentMethodOptions;
	}

	public List<AdyenSubscriptionPaymentMethodOptionWsDTO> getAdyenVaultOptions()
	{
		return adyenVaultOptions;
	}

	public void setAdyenVaultOptions(final List<AdyenSubscriptionPaymentMethodOptionWsDTO> adyenVaultOptions)
	{
		this.adyenVaultOptions = adyenVaultOptions == null ? new ArrayList<>() : adyenVaultOptions;
	}
}
