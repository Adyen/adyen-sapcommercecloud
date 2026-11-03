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
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.repository;

import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_PROVIDER;

/**
 * Repository class for PaymentTransactions
 */
public class PaymentTransactionRepository extends AbstractRepository {
    private static final Logger LOG = Logger.getLogger(PaymentTransactionRepository.class);

    public PaymentTransactionModel getTransactionModel(String pspReference) {
        final Map queryParams = new HashMap();
        queryParams.put("paymentProvider", PAYMENT_PROVIDER);
        queryParams.put("requestId", pspReference);
        // Joined to PaymentTransactionEntry: {requestId} lives on the entry (the AUTHORIZATION/CAPTURE/etc.
        // pspReference), not on PaymentTransaction itself. A prior version of this query matched
        // PaymentTransaction's own (coincidentally same-named) requestId column instead of the entry's,
        // which only happened to work when both values matched by chance.
        final FlexibleSearchQuery selectOrderQuery = new FlexibleSearchQuery(
                "SELECT DISTINCT {pt:pk} FROM {" + PaymentTransactionModel._TYPECODE + " AS pt JOIN "
                        + PaymentTransactionEntryModel._TYPECODE + " AS pte ON {pte:" + PaymentTransactionEntryModel.PAYMENTTRANSACTION + "} = {pt:pk}}"
                        + " WHERE {pt:" + PaymentTransactionModel.PAYMENTPROVIDER + "} = ?paymentProvider"
                        + " AND {pte:" + PaymentTransactionEntryModel.REQUESTID + "} = ?requestId"
                        //Adding "{versionID} IS NULL" to get the original order regardless of modification history
                        + " AND {pt:versionID} IS NULL",
                queryParams
        );

        LOG.debug("Finding transaction with PSP reference: " + pspReference);

        return (PaymentTransactionModel) getOneOrNull(selectOrderQuery);
    }
}
