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
 * The properties the subscriptions page reads through EL, asserted to exist as JavaBeans properties.
 *
 * <h3>Why this test exists</h3>
 * <p>A method called {@code tone()} is not a property called {@code tone}. EL resolves
 * {@code ${subscription.state.tone}} by introspection, finds no {@code getTone}, and throws
 * {@code PropertyNotFoundException}; the CMS component renderer catches that and renders an <em>empty
 * slot</em>. So the symptom of a renamed or mis-prefixed accessor is not a stack trace on the page but a
 * blank one, and the compiler has nothing to say about it because the only caller is a JSP.</p>
 *
 * <p>That shipped once. It cost a build, a deploy and a restart to find something introspection answers in
 * a millisecond.</p>
 *
 * <h3>How to use it</h3>
 * <p>The lists below are the view contract, maintained by hand on purpose: adding an EL expression to the
 * page means adding its property here, and that deliberate step is the point. A name removed from the DTO
 * while the page still reads it fails here instead of on a deployed environment.</p>
 */
@UnitTest
public class SubscriptionViewContractTest
{
	/** Read on each row of the list. */
	private static final List<String> ENTRY_PROPERTIES = Arrays.asList(
			"code", "productName", "quantity", "state", "effectiveDate",
			"orderCode", "orderDate", "paymentMethodSummary",
			"cancellable", "manageable",
			"paymentMethodChangeScope", "paymentMethodChangeable", "paymentMethodChangeCovered");

	/** Read on the overview, by the controller rather than the page, but the same contract. */
	private static final List<String> OVERVIEW_PROPERTIES = Arrays.asList(
			"subscriptions", "ordersAwaitingSetup", "paymentMethodSubscriptionCode",
			"paymentMethodChangeScope", "anyPaymentMethodChangeable",
			"paymentMethodChangeSupportedSomewhere", "empty");

	/** Read on the state enum from inside a row: {@code ${subscription.state.<name>}}. */
	private static final List<String> STATE_PROPERTIES = Arrays.asList(
			"tone", "cancellable", "paymentMethodChangeable");

	@Test
	public void everyRowPropertyTheePageReadsIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(SubscriptionEntryData.class, ENTRY_PROPERTIES);
	}

	@Test
	public void everyOverviewPropertyIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(SubscriptionOverviewData.class, OVERVIEW_PROPERTIES);
	}

	/**
	 * The enum is the case that actually broke: a plain method on it reads perfectly well in Java and is
	 * invisible to EL.
	 */
	@Test
	public void everyStatePropertyReadFromARowIsResolvableByEl() throws IntrospectionException
	{
		assertResolvable(SubscriptionDisplayState.class, STATE_PROPERTIES);
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
