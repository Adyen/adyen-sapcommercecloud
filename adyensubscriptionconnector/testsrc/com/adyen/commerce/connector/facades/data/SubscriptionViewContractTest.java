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
package com.adyen.commerce.connector.facades.data;

import static org.junit.Assert.assertTrue;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * The properties the subscriptions page and its tags read through EL, asserted to exist as JavaBeans
 * properties.
 *
 * <p>A method called {@code tone()} is not a property called {@code tone}: EL resolves
 * {@code ${subscription.state.tone}} by introspection, finds no {@code getTone} and throws
 * {@code PropertyNotFoundException}, which the CMS component renderer catches and turns into an empty slot.
 * The symptom of a renamed or mis-prefixed accessor is therefore a blank page rather than a stack trace,
 * and the compiler says nothing because the only caller is a JSP.</p>
 *
 * <p>The lists below are the view contract and are maintained by hand: adding an EL expression to the page
 * means adding its property here.</p>
 */
@UnitTest
public class SubscriptionViewContractTest
{
	/** Read by platformSection.tag on the section it renders. */
	private static final List<String> SECTION_PROPERTIES = Arrays.asList(
			"platformCode", "platformName", "subscriptions", "ordersAwaitingSetup",
			"paymentMethodChangeOffers", "paymentMethodEnrollmentOffers", "paymentMethodChangeSupported",
			"paymentMethodChangeOffered");

	/** Read on each row of a section. */
	private static final List<String> ENTRY_PROPERTIES = Arrays.asList(
			"code", "productName", "quantity", "state", "effectiveDate",
			"orderCode", "orderDate", "paymentMethodSummary",
			"cancellable", "paymentMethodControl", "paymentMethodChangeCovered",
			"paymentMethodOptions", "adyenVaultOptions", "paymentMethodShareable");

	/** Read on the overview by the controller. */
	private static final List<String> OVERVIEW_PROPERTIES = Arrays.asList("sections", "empty");

	private static final List<String> CHANGE_OFFER_PROPERTIES = Arrays.asList(
			"subscriptionCode", "accountName", "paymentMethodOptions", "adyenVaultOptions");

	private static final List<String> ENROLLMENT_OFFER_PROPERTIES = Arrays.asList(
			"subscriptionCode", "accountName", "notice");

	/** Read on each option by paymentMethodOptions.tag. */
	private static final List<String> OPTION_PROPERTIES = Arrays.asList("id", "displayLabel", "current");

	/** Read on the state enum from inside a row: {@code ${subscription.state.<name>}}. */
	private static final List<String> STATE_PROPERTIES = Arrays.asList(
			"tone", "cancellable", "paymentMethodChangeable");

	@Test
	public void everySectionPropertyTheTagReadsIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(SubscriptionPlatformSectionData.class, SECTION_PROPERTIES);
	}

	@Test
	public void everyRowPropertyThePageReadsIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(SubscriptionEntryData.class, ENTRY_PROPERTIES);
	}

	@Test
	public void everyOverviewPropertyIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(SubscriptionOverviewData.class, OVERVIEW_PROPERTIES);
	}

	@Test
	public void everyOfferPropertyIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(PaymentMethodChangeOfferData.class, CHANGE_OFFER_PROPERTIES);
		assertResolvable(PaymentMethodEnrollmentOfferData.class, ENROLLMENT_OFFER_PROPERTIES);
	}

	/** A plain method on the enum reads perfectly well in Java and is invisible to EL. */
	@Test
	public void everyStatePropertyReadFromARowIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(SubscriptionDisplayState.class, STATE_PROPERTIES);
	}

	@Test
	public void everyOptionPropertyThePickerReadsIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(PaymentMethodOptionData.class, OPTION_PROPERTIES);
	}

	private static void assertResolvable(final Class<?> type, final List<String> properties)
			throws IntrospectionException
	{
		final BeanInfo info = Introspector.getBeanInfo(type);
		final List<String> readable = Arrays.stream(info.getPropertyDescriptors())
				.filter(descriptor -> descriptor.getReadMethod() != null)
				.map(PropertyDescriptor::getName)
				.collect(Collectors.toList());

		for (final String property : properties)
		{
			assertTrue(type.getSimpleName() + " has no readable JavaBeans property '" + property
					+ "', so ${...." + property + "} raises PropertyNotFoundException and the CMS slot "
					+ "renders empty. Readable properties are " + readable, readable.contains(property));
		}
	}
}
