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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

import com.adyen.v6.constants.Adyenv6coreConstants;

import de.hybris.platform.core.PK;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.type.TypeService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.tx.Transaction;
import de.hybris.platform.tx.TransactionBody;

/**
 * Encrypts the webhook HMAC keys that were stored in clear text before {@code BaseStore.adyenNotificationHMACKey}
 * became an encrypted attribute.
 *
 * <p>With the platform's default (no clear-text fallback) reading a clear-text value of an encrypted attribute
 * through the model throws, so the legacy values are read with plain JDBC, and each store that still holds one
 * is saved again through the model, which encrypts the value (see {@link #saveEncrypted}). Idempotent: a second run finds only encrypted
 * values and writes nothing. A key is never logged, not even in part.</p>
 */
@SystemSetup(extension = Adyenv6coreConstants.EXTENSIONNAME)
public class AdyenNotificationHmacKeyEncryptionSetup
{
	private static final Logger LOG = Logger.getLogger(AdyenNotificationHmacKeyEncryptionSetup.class);

	private static final String BASE_STORE_TYPE = "BaseStore";
	/** Key id, ':', then at least the 24-character IV plus one 16-byte block in Base64 (24 more characters). */
	private static final Pattern ENCRYPTED_SHAPE = Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9+/=;]{48,}");

	private ModelService modelService;
	private TypeService typeService;

	/**
	 * A raw row of the base store table: the primary key and the stored, possibly clear-text, value.
	 */
	protected record RawKey(long pk, String value)
	{
	}

	@SystemSetup(type = SystemSetup.Type.ESSENTIAL, process = SystemSetup.Process.ALL)
	public void encryptNotificationHmacKeys()
	{
		if (!isAttributeEncrypted())
		{
			LOG.warn("Webhook HMAC key encryption skipped: BaseStore.adyenNotificationHMACKey is not an encrypted "
					+ "attribute yet. Run the system update with 'Update running system' selected.");
			return;
		}

		int migrated = 0;
		int alreadyEncrypted = 0;
		int withoutKey = 0;
		int unreadable = 0;
		int failed = 0;

		final List<RawKey> rows = readRawKeys();
		LOG.debug("Webhook HMAC key encryption: inspecting " + rows.size() + " base stores");
		// Inside a caller's transaction a failed store marks that whole transaction rollback-only, so every store
		// counted as migrated would be rolled back at its commit: stop at the first failure instead.
		final boolean nested = isInsideTransaction();

		for (final RawKey row : rows)
		{
			if (StringUtils.isBlank(row.value()))
			{
				withoutKey++;
			}
			else if (looksEncrypted(row.value()))
			{
				if (decrypts(row.value()))
				{
					alreadyEncrypted++;
				}
				else
				{
					// Never re-encrypt it: encrypting a ciphertext again (e.g. a database copied from an
					// environment with another symmetric key) would destroy the key for good.
					LOG.warn("Webhook HMAC key encryption: the key of base store PK " + row.pk()
							+ " is encrypted but cannot be decrypted with this installation's key; left unchanged. "
							+ "Regenerate the HMAC key in the Adyen Customer Area and save it again.");
					unreadable++;
				}
			}
			else
			{
				BaseStoreModel store = null;
				try
				{
					store = getModelService().get(PK.fromLong(row.pk()));
					saveEncrypted(store, row.value());
					migrated++;
				}
				catch (final RuntimeException e)
				{
					// Class names only: the message of a conversion or save error could carry the value.
					LOG.warn("Webhook HMAC key encryption failed for base store PK " + row.pk() + ": "
							+ describeCauses(e));
					failed++;
					if (nested)
					{
						LOG.error("Webhook HMAC key encryption aborted: it runs inside an outer transaction, which this "
								+ "failure marked for rollback, so no store is migrated. Run the step again outside a "
								+ "transaction (system update or HAC scripting console).");
						throw new IllegalStateException("Webhook HMAC key encryption aborted inside an outer transaction");
					}
				}
				finally
				{
					// A failed store would otherwise stay dirty with the clear-text key and be saved again by
					// a later saveAll() of another setup step.
					detachQuietly(store);
				}
			}
		}

		final String summary = "Webhook HMAC key encryption: " + migrated + " base stores migrated, " + alreadyEncrypted
				+ " already encrypted, " + withoutKey + " without a key, " + unreadable + " encrypted but unreadable, "
				+ failed + " failed.";
		if (failed > 0 || unreadable > 0)
		{
			LOG.error(summary + " Webhooks of the affected stores are rejected until their key is fixed.");
		}
		else
		{
			LOG.info(summary);
		}
	}

	/**
	 * Saves the clear-text key so that it is stored encrypted. A single save cannot do it: the legacy persistence
	 * layer ({@code ExtensibleItemEJB.setProperty}) decrypts the previous stored value to return it, which throws
	 * for clear text while {@code toggle.disallowTaeEncryptionFallback.enabled=true}. A null value is not
	 * decrypted, so the key is cleared first and then set again. Both saves run in one transaction, so other
	 * sessions and cluster nodes never see the store without a key.
	 */
	protected void saveEncrypted(final BaseStoreModel store, final String clearTextKey)
	{
		inTransaction(() -> {
			// The setter marks the attribute dirty, which also clears the load error that reading the
			// clear-text value recorded on the model.
			store.setAdyenNotificationHMACKey(null);
			getModelService().save(store);
			store.setAdyenNotificationHMACKey(clearTextKey);
			getModelService().save(store);
		});
	}

	protected boolean isInsideTransaction()
	{
		return Transaction.current().isRunning();
	}

	/** Runs the work in a platform transaction; a runtime exception rolls it back and is rethrown. */
	protected void inTransaction(final Runnable work)
	{
		try
		{
			Transaction.current().execute(new TransactionBody()
			{
				@Override
				public Object execute()
				{
					work.run();
					return null;
				}
			});
		}
		catch (final RuntimeException e)
		{
			// Rethrown as is, so the caller logs the original class; only checked exceptions are wrapped below.
			throw e;
		}
		catch (final Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Without an encrypted attribute (type system not updated in this run) a save would only write the clear text
	 * back, so the step does nothing.
	 */
	protected boolean isAttributeEncrypted()
	{
		return Boolean.TRUE.equals(getTypeService()
				.getAttributeDescriptor(BASE_STORE_TYPE, BaseStoreModel.ADYENNOTIFICATIONHMACKEY).getEncrypted());
	}

	/** The class names of the exception and its causes, never their messages. */
	protected static String describeCauses(final Throwable error)
	{
		final StringBuilder names = new StringBuilder();
		Throwable current = error;
		for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause())
		{
			if (names.length() > 0)
			{
				names.append(" <- ");
			}
			names.append(current.getClass().getName());
		}
		return names.toString();
	}

	protected void detachQuietly(final BaseStoreModel store)
	{
		if (store == null)
		{
			return;
		}
		try
		{
			getModelService().detach(store);
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Could not detach base store after HMAC key encryption: " + e.getClass().getName());
		}
	}

	/**
	 * Reads the stored values bypassing the model, which would refuse a clear-text value. The table and column
	 * come from the type system.
	 */
	protected List<RawKey> readRawKeys()
	{
		final String table = getTypeService().getComposedTypeForCode(BASE_STORE_TYPE).getTable();
		final String column = getTypeService()
				.getAttributeDescriptor(BASE_STORE_TYPE, BaseStoreModel.ADYENNOTIFICATIONHMACKEY).getDatabaseColumn();
		final JdbcTemplate jdbcTemplate = new JdbcTemplate(Registry.getCurrentTenant().getDataSource());
		return new ArrayList<>(jdbcTemplate.query("SELECT PK, " + column + " FROM " + table,
				(rs, rowNum) -> new RawKey(rs.getLong(1), rs.getString(2))));
	}

	/**
	 * True when the value has the shape of the platform's stored encrypted form: {@code <keyId>:} followed by the
	 * Base64 IV (24 characters) and the Base64 ciphertext (at least one block), for example 134 characters for a
	 * 64-character key. Some platform versions put a data-type marker such as {@code ;S;} between them, so ';' is
	 * allowed. A clear-text HMAC key is hexadecimal and never contains ':'.
	 */
	protected boolean looksEncrypted(final String value)
	{
		return ENCRYPTED_SHAPE.matcher(value).matches();
	}

	/** True when this installation's key decrypts the value. */
	protected boolean decrypts(final String value)
	{
		try
		{
			tryDecrypt(value);
			return true;
		}
		catch (final RuntimeException e)
		{
			return false;
		}
	}

	/** Decrypts the value only to check that it can be decrypted; throws when it cannot. The result is discarded. */
	protected void tryDecrypt(final String value)
	{
		Registry.getMasterTenant().getValueEncryptor().decrypt(value);
	}

	protected ModelService getModelService()
	{
		return modelService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	protected TypeService getTypeService()
	{
		return typeService;
	}

	public void setTypeService(final TypeService typeService)
	{
		this.typeService = typeService;
	}
}
