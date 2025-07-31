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
package com.adyen.v6.model;

import javax.servlet.http.HttpServletRequest;

import com.adyen.v6.constants.StorefrontType;

public class RequestInfo {

    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String ACCEPT_HEADER = "Accept";

    private String userAgent;
    private String acceptHeader;
    private String shopperIp;
    private String origin;
    private String shopperLocale;
    private StorefrontType storefrontType;
    private String storefrontVersion;

    public RequestInfo(HttpServletRequest request) {
        this.userAgent = request.getHeader(USER_AGENT_HEADER);
        this.acceptHeader = request.getHeader(ACCEPT_HEADER);
        this.shopperIp = request.getRemoteAddr();
        this.origin = getOrigin(request);
    }

    public RequestInfo() {
    }

    public String getOrigin(HttpServletRequest request) {
        String currentRequestURL = request.getRequestURL().toString();
        int requestUrlLength = currentRequestURL.length();
        int requestUriLength = request.getRequestURI().length();

        String baseURL = currentRequestURL.substring(0, requestUrlLength - requestUriLength);
        return baseURL;
    }

    public String getOrigin() {
        return this.origin;
    }

    public static RequestInfo empty() {
        return new RequestInfo();
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public String getShopperIp() {
        return shopperIp;
    }

    public String getShopperLocale() {
        return shopperLocale;
    }

    public void setShopperLocale(String shopperLocale) {
        this.shopperLocale = shopperLocale;
    }

    public String getStorefrontVersion() {
        return storefrontVersion;
    }

    public void setStorefrontVersion(String storefrontVersion) {
        this.storefrontVersion = storefrontVersion;
    }

    public StorefrontType getStorefrontType() {
        return storefrontType;
    }

    public void setStorefrontType(StorefrontType storefrontType) {
        this.storefrontType = storefrontType;
    }
}
