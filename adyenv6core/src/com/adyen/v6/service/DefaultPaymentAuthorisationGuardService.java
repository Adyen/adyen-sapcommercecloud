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
package com.adyen.v6.service;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.v6.repository.CartRepository;

import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;

/**
 * Resolves the cart being paid for and offers it to each guard in order. With no guard registered it does nothing,
 * not even the lookup.
 *
 * <p>The cart is the session cart when its code is the one in {@code cartData}, which is the case on every path
 * today (express checkout sets the cart it pays for as the session cart); otherwise it is looked up by code, so a
 * guard never judges a different cart from the one that is charged.</p>
 */
public class DefaultPaymentAuthorisationGuardService implements PaymentAuthorisationGuardService
{
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPaymentAuthorisationGuardService.class);

    private List<PaymentAuthorisationGuard> guards;
    private CartService cartService;
    private CartRepository cartRepository;

    @Override
    public void check(final CartData cartData, final PaymentAuthorisationStage stage)
    {
        if (CollectionUtils.isEmpty(getGuards()) || cartData == null)
        {
            return;
        }
        final CartModel cart = resolveCart(cartData);
        if (cart == null)
        {
            // The payment cannot be placed without this cart, so there is nothing to protect; do not stop it here.
            LOG.warn("event=payment_authorisation_guard_skipped reason=cart_not_found stage={}", stage);
            return;
        }
        check(cart, stage);
    }

    @Override
    public void check(final AbstractOrderModel cart, final PaymentAuthorisationStage stage)
    {
        if (CollectionUtils.isEmpty(getGuards()) || cart == null)
        {
            return;
        }
        for (final PaymentAuthorisationGuard guard : getGuards())
        {
            try
            {
                guard.beforeAuthorisation(cart, stage);
            }
            catch (final PaymentAuthorisationRefusedException e)
            {
                LOG.info("event=payment_authorisation_refused stage={} error_code={}", stage, e.getErrorCode());
                throw e;
            }
        }
    }

    protected List<PaymentAuthorisationGuard> getGuards()
    {
        return guards;
    }

    protected CartService getCartService()
    {
        return cartService;
    }

    protected CartRepository getCartRepository()
    {
        return cartRepository;
    }

    protected CartModel resolveCart(final CartData cartData)
    {
        if (getCartService() != null && getCartService().hasSessionCart())
        {
            final CartModel sessionCart = getCartService().getSessionCart();
            if (sessionCart != null && StringUtils.equals(sessionCart.getCode(), cartData.getCode()))
            {
                return sessionCart;
            }
        }
        return StringUtils.isBlank(cartData.getCode()) || getCartRepository() == null ? null
                : getCartRepository().getCart(cartData.getCode());
    }

    public void setGuards(final List<PaymentAuthorisationGuard> guards)
    {
        this.guards = guards;
    }

    public void setCartService(final CartService cartService)
    {
        this.cartService = cartService;
    }

    public void setCartRepository(final CartRepository cartRepository)
    {
        this.cartRepository = cartRepository;
    }
}
