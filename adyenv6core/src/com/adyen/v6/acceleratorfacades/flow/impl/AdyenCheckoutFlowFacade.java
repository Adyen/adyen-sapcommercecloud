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
import de.hybris.platform.core.model.order.CartModel;

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
            
            // Ensure payment info is refreshed from the cart model
            // This is needed for API endpoints where payment info is set after cart creation
            if (cartData.getPaymentInfo() == null && getCartService().hasSessionCart())
            {
                final CartModel cartModel = getCartService().getSessionCart();
                if (cartModel.getPaymentInfo() != null)
                {
                    // Re-convert the cart to get the updated payment info
                    final CartData refreshedCartData = getCartFacade().getSessionCart();
                    if (refreshedCartData != null)
                    {
                        refreshedCartData.setDeliveryAddress(getDeliveryAddress());
                        refreshedCartData.setDeliveryMode(getDeliveryMode());
                        return refreshedCartData;
                    }
                }
            }
        }

        return cartData;
    }
}
