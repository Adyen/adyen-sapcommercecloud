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
package com.adyen.v6.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.PK;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link AdyenNotificationHmacKeyEncryptionSetup}.
 */
@UnitTest
public class AdyenNotificationHmacKeyEncryptionSetupTest
{
	/** The stored form seen on 2211-jdk21: key id, ':', 24-character IV, Base64 ciphertext; 134 characters. */
	private static final String ENCRYPTED = "1:" + "q83vEjRWeJCrze8SNFZ4kA==" + "A".repeat(106) + "==";
	/** A variant with a data-type marker, as described for other platform versions. */
	private static final String ENCRYPTED_WITH_MARKER = "1:" + "q83vEjRWeJCrze8SNFZ4kA==" + ";S;" + "A".repeat(106)
			+ "==";
	private static final String CLEAR = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	private ModelService modelService;
	private List<AdyenNotificationHmacKeyEncryptionSetup.RawKey> rows;
	private Set<String> undecryptable = Set.of();
	private boolean attributeEncrypted = true;
	private boolean rawKeysRead;
	private boolean nested;
	private AdyenNotificationHmacKeyEncryptionSetup setup;

	@Before
	public void setUp()
	{
		modelService = mock(ModelService.class);
		setup = new AdyenNotificationHmacKeyEncryptionSetup()
		{
			@Override
			protected boolean isInsideTransaction()
			{
				return nested;
			}

			@Override
			protected void inTransaction(final Runnable work)
			{
				work.run();
			}

			@Override
			protected boolean isAttributeEncrypted()
			{
				return attributeEncrypted;
			}

			@Override
			protected List<RawKey> readRawKeys()
			{
				rawKeysRead = true;
				return rows;
			}

			@Override
			protected void tryDecrypt(final String value)
			{
				if (undecryptable.contains(value))
				{
					throw new IllegalStateException("error in decryption");
				}
			}
		};
		setup.setModelService(modelService);
	}

	private BaseStoreModel storeFor(final long pk)
	{
		final BaseStoreModel store = mock(BaseStoreModel.class);
		when(modelService.get(PK.fromLong(pk))).thenReturn(store);
		return store;
	}

	@Test
	public void clearTextKeyIsSetAndSavedWithTheExactRawValue()
	{
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, CLEAR));
		final BaseStoreModel store = storeFor(1L);

		setup.encryptNotificationHmacKeys();

		// Cleared first: a single save would make the platform decrypt the clear-text old value and fail.
		final InOrder order = inOrder(store, modelService);
		order.verify(store).setAdyenNotificationHMACKey(null);
		order.verify(modelService).save(store);
		order.verify(store).setAdyenNotificationHMACKey(CLEAR);
		order.verify(modelService).save(store);
	}

	@Test
	public void bothSavesRunInOneTransaction()
	{
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, CLEAR));
		final BaseStoreModel store = storeFor(1L);
		final int[] transactions = { 0 };
		final AdyenNotificationHmacKeyEncryptionSetup counting = new AdyenNotificationHmacKeyEncryptionSetup()
		{
			@Override
			protected boolean isInsideTransaction()
			{
				return nested;
			}

			@Override
			protected void inTransaction(final Runnable work)
			{
				transactions[0]++;
				verify(modelService, never()).save(any(Object.class));
				work.run();
			}

			@Override
			protected boolean isAttributeEncrypted()
			{
				return true;
			}

			@Override
			protected List<RawKey> readRawKeys()
			{
				return rows;
			}
		};
		counting.setModelService(modelService);

		counting.encryptNotificationHmacKeys();

		assertEquals(1, transactions[0]);
		verify(modelService, times(2)).save(store);
	}

	@Test
	public void doesNothingWhileTheAttributeIsNotEncryptedInTheTypeSystem()
	{
		attributeEncrypted = false;
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, CLEAR));

		setup.encryptNotificationHmacKeys();

		assertFalse(rawKeysRead);
		verifyNoInteractions(modelService);
	}

	@Test
	public void describesCausesByClassNameWithoutMessages()
	{
		final String description = AdyenNotificationHmacKeyEncryptionSetup
				.describeCauses(new IllegalStateException("outer " + CLEAR, new IllegalArgumentException(CLEAR)));

		assertEquals("java.lang.IllegalStateException <- java.lang.IllegalArgumentException", description);
		assertFalse(description.contains(CLEAR));
	}

	@Test
	public void recognisesTheStoredEncryptedShapeAndNeverAClearTextKey()
	{
		assertEquals(134, ENCRYPTED.length());
		assertTrue(setup.looksEncrypted(ENCRYPTED));
		assertTrue(setup.looksEncrypted(ENCRYPTED_WITH_MARKER));
		assertFalse(setup.looksEncrypted(CLEAR));
		assertFalse(setup.looksEncrypted(CLEAR.toUpperCase()));
		assertFalse(setup.looksEncrypted("1:short"));
		// A key of 1 to 15 characters: IV plus one block, 50 characters (seen at runtime for an 8-character value).
		final String shortKeyEncrypted = "1:" + "q83vEjRWeJCrze8SNFZ4kA==" + "A".repeat(22) + "==";
		assertEquals(50, shortKeyEncrypted.length());
		assertTrue(setup.looksEncrypted(shortKeyEncrypted));
	}

	@Test
	public void abortsAtTheFirstFailureInsideAnOuterTransaction()
	{
		nested = true;
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, CLEAR),
				new AdyenNotificationHmacKeyEncryptionSetup.RawKey(2L, CLEAR + "x"));
		final BaseStoreModel first = storeFor(1L);
		final BaseStoreModel second = storeFor(2L);
		doThrow(new IllegalStateException("boom")).when(modelService).save(first);

		assertThrows(IllegalStateException.class, () -> setup.encryptNotificationHmacKeys());

		verify(modelService).detach(first);
		verifyNoInteractions(second);
	}

	@Test
	public void encryptedKeyWithoutMarkerIsNotEncryptedAgain()
	{
		// Regression: a filter that required ";S;" re-encrypted this form on the second run.
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, ENCRYPTED));

		setup.encryptNotificationHmacKeys();
		setup.encryptNotificationHmacKeys();

		verifyNoInteractions(modelService);
	}

	@Test
	public void encryptedKeyIsNotWritten()
	{
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, ENCRYPTED));

		setup.encryptNotificationHmacKeys();

		verifyNoInteractions(modelService);
	}

	@Test
	public void encryptedValueThatFailsToDecryptIsLeftUnchanged()
	{
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, ENCRYPTED));
		undecryptable = Set.of(ENCRYPTED);

		setup.encryptNotificationHmacKeys();

		verifyNoInteractions(modelService);
	}

	@Test
	public void nullAndEmptyKeysAreSkipped()
	{
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, null),
				new AdyenNotificationHmacKeyEncryptionSetup.RawKey(2L, ""),
				new AdyenNotificationHmacKeyEncryptionSetup.RawKey(3L, "  "));

		setup.encryptNotificationHmacKeys();

		verifyNoInteractions(modelService);
	}

	@Test
	public void saveFailureOnOneStoreDoesNotStopTheNext()
	{
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, CLEAR),
				new AdyenNotificationHmacKeyEncryptionSetup.RawKey(2L, CLEAR + "x"));
		final BaseStoreModel first = storeFor(1L);
		final BaseStoreModel second = storeFor(2L);
		doThrow(new IllegalStateException("boom " + CLEAR)).when(modelService).save(first);

		setup.encryptNotificationHmacKeys();

		verify(modelService).save(first);
		verify(modelService).detach(first);
		verify(second).setAdyenNotificationHMACKey(CLEAR + "x");
		verify(modelService, times(2)).save(second);
	}

	@Test
	public void secondRunWritesNothingOnceAllKeysAreEncrypted()
	{
		rows = List.of(new AdyenNotificationHmacKeyEncryptionSetup.RawKey(1L, ENCRYPTED),
				new AdyenNotificationHmacKeyEncryptionSetup.RawKey(2L, ENCRYPTED));

		setup.encryptNotificationHmacKeys();
		setup.encryptNotificationHmacKeys();

		verify(modelService, never()).save(any(Object.class));
	}
}
