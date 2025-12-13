/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Hybris Extension
 *
 * Copyright (c) 2020 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 */

package com.adyen.v6.interceptors;

import com.adyen.v6.utils.SameSiteCookieAttributeAppenderUtils;
import org.springframework.web.servlet.ModelAndView;
//import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * This class uses code written by Igor Zarvanskyi and published on https://clutcher.github.io/post/hybris/same_site_login_issue/
 */
public class SameSiteCookieHandlerInterceptorAdapter /*extends HandlerInterceptorAdapter*/ {

    private SameSiteCookieAttributeAppenderUtils sameSiteCookieAttributeAppenderUtils;

    //@Override
    public void postHandle(HttpServletRequest servletRequest, HttpServletResponse servletResponse, Object handler, ModelAndView modelAndView) {
        getSameSiteCookieAttributeAppenderUtils().addSameSiteAttribute(servletRequest, servletResponse);
    }

    protected SameSiteCookieAttributeAppenderUtils getSameSiteCookieAttributeAppenderUtils() {
        return sameSiteCookieAttributeAppenderUtils;
    }

    public void setSameSiteCookieAttributeAppenderUtils(SameSiteCookieAttributeAppenderUtils sameSiteCookieAttributeAppenderUtils) {
        this.sameSiteCookieAttributeAppenderUtils = sameSiteCookieAttributeAppenderUtils;
    }
}