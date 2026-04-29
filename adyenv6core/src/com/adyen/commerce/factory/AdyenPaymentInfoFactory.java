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
package com.adyen.commerce.factory;

import com.adyen.v6.forms.AdyenPaymentForm;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;

/**
 * Factory responsible for creating {@link PaymentInfoModel} instances from different payment input sources.
 */
public interface AdyenPaymentInfoFactory {

    /**
     * Creates and persists a {@link PaymentInfoModel} from an Accelerator {@link AdyenPaymentForm}.
     *
     * @param cartModel        the current session cart
     * @param adyenPaymentForm the submitted payment form
     * @return the saved {@link PaymentInfoModel}
     */
    PaymentInfoModel createFromPaymentForm(CartModel cartModel, AdyenPaymentForm adyenPaymentForm);

    /**
     * Creates and persists a {@link PaymentInfoModel} from an OCC {@link PaymentDetailsWsDTO}.
     *
     * @param cartModel      the current session cart
     * @param paymentDetails the payment details DTO from the OCC request
     * @return the saved {@link PaymentInfoModel}
     */
    PaymentInfoModel createFromPaymentDetails(CartModel cartModel, PaymentDetailsWsDTO paymentDetails);
}
