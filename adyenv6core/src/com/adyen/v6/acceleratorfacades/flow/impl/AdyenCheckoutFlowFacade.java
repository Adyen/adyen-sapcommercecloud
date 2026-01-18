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
package com.adyen.v6.acceleratorfacades.flow.impl;

import de.hybris.platform.acceleratorfacades.flow.impl.DefaultCheckoutFlowFacade;
import de.hybris.platform.commercefacades.order.data.CartData;

public class AdyenCheckoutFlowFacade extends DefaultCheckoutFlowFacade
{
    @Override
    public CartData getCheckoutCart()
    {
        final CartData cartData = getCartFacade().getSessionCart();
        if (cartData != null)
        {
            cartData.setDeliveryAddress(getDeliveryAddress());
            cartData.setDeliveryMode(getDeliveryMode());
        }

        return cartData;
    }
}
