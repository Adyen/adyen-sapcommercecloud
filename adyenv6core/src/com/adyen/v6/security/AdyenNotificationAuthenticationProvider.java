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
package com.adyen.v6.security;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.security.SignatureException;
import java.util.Base64;

/**
 * Authenticates a request that is using Basic Authentication
 */
@Component
public class AdyenNotificationAuthenticationProvider {
    @Resource(name = "baseStoreService")
    private BaseStoreService baseStoreService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    private static final Logger LOG = Logger.getLogger(AdyenNotificationAuthenticationProvider.class);

    public boolean authenticate(final HttpServletRequest request, NotificationRequest notificationRequest, String baseSiteId) {
        LOG.debug("Trying to authenticate for baseSiteId " + baseSiteId);

        BaseStoreModel baseStore = getBaseStore(baseSiteId);

        boolean basicAuthenticated = authenticateBasic(request, baseStore);
        boolean checkHMAC = checkHMACFromAdditionalData(notificationRequest, baseStore);

        return basicAuthenticated && checkHMAC;
    }

    public boolean authenticate(final HttpServletRequest request, final String requestBody, String baseSiteId) {
        BaseStoreModel baseStore = getBaseStore(baseSiteId);

        boolean basicAuthenticated = authenticateBasic(request, baseStore);
        boolean checkHMAC = checkHMACFromHeader(request, requestBody, baseStore);

        return basicAuthenticated && checkHMAC;
    }

    protected BaseStoreModel getBaseStore(final String baseSiteId) {
        final BaseSiteModel requestedBaseSite = getBaseSiteService().getBaseSiteForUID(baseSiteId);
        if (requestedBaseSite != null) {
            final BaseSiteModel currentBaseSite = getBaseSiteService().getCurrentBaseSite();

            if (!requestedBaseSite.equals(currentBaseSite)) {
                getBaseSiteService().setCurrentBaseSite(requestedBaseSite, true);
            }
            BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

            if (baseStore == null) {
                throw new RuntimeException("BaseStore does not exist for baseSite: " + baseSiteId);
            }
            return baseStore;
        }
        throw new RuntimeException("BaseSite does not exist: " + baseSiteId);
    }

    protected boolean authenticateBasic(final HttpServletRequest request, BaseStoreModel baseStoreModel) {
        final String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Basic")) {
            String base64Credentials = authorization.substring("Basic".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), Charset.forName("UTF-8"));
            final String[] values = credentials.split(":", 2);
            return tryToAuthenticate(values[0], values[1], baseStoreModel);
        }
        return false;
    }

    protected boolean tryToAuthenticate(String name, String password, BaseStoreModel baseStore) {

        String notificationUsername = baseStore.getAdyenNotificationUsername();
        String notificationPassword = baseStore.getAdyenNotificationPassword();

        Assert.notNull(notificationUsername);
        Assert.notNull(notificationPassword);

        if (notificationUsername.isEmpty() || notificationPassword.isEmpty()) {
            return false;
        }

        if (notificationUsername.equals(name) && notificationPassword.equals(password)) {
            return true;
        }

        return false;
    }

    protected boolean checkHMACFromAdditionalData(NotificationRequest notificationRequest, BaseStoreModel baseStore) {
        String hmacKey = baseStore.getAdyenNotificationHMACKey();

        if (StringUtils.isNotEmpty(hmacKey)) {
            HMACValidator hmacValidator = new HMACValidator();
            try {
                for (NotificationRequestItem notificationItem : notificationRequest.getNotificationItems()) {
                    if (!hmacValidator.validateHMAC(notificationItem, hmacKey)) {
                        LOG.error("Signature check failed");
                        return false;
                    }
                }
            } catch (IllegalArgumentException | SignatureException e) {
                LOG.error("Signature check exception");
                return false;
            }
            return true;
        }
        LOG.warn("HMAC authentication not configured");
        return true;
    }

    protected boolean checkHMACFromHeader(final HttpServletRequest request, final String requestBody, BaseStoreModel baseStore) {
        String hmacSignature = request.getHeader("hmacsignature");
        String hmacKey = baseStore.getAdyenNotificationHMACKey();

        if (StringUtils.isNotEmpty(hmacKey)) {
            HMACValidator hmacValidator = new HMACValidator();
            try {
                if (!hmacValidator.validateHMAC(hmacSignature, hmacKey, requestBody)) {
                    LOG.error("Signature check failed");
                    return false;
                }
            } catch (IllegalArgumentException | SignatureException e) {
                LOG.error("Signature check exception");
                return false;
            }
            return true;
        }
        LOG.warn("HMAC authentication not configured");
        return true;
    }

    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    protected BaseSiteService getBaseSiteService() {
        return baseSiteService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }
}
