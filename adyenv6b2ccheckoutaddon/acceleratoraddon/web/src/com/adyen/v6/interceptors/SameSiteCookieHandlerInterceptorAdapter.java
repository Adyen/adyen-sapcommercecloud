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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/*
 * This class uses code written by Igor Zarvanskyi and published on https://clutcher.github.io/post/hybris/same_site_login_issue/
 */
public class SameSiteCookieHandlerInterceptorAdapter implements HandlerInterceptor {

    private SameSiteCookieAttributeAppenderUtils sameSiteCookieAttributeAppenderUtils;

    @Override
    public void postHandle(HttpServletRequest servletRequest, HttpServletResponse servletResponse, Object handler, ModelAndView modelAndView) throws Exception {
        if (getSameSiteCookieAttributeAppenderUtils() != null) {
            getSameSiteCookieAttributeAppenderUtils().addSameSiteAttribute(servletRequest, servletResponse);
        }
    }

    public SameSiteCookieAttributeAppenderUtils getSameSiteCookieAttributeAppenderUtils() {
        return sameSiteCookieAttributeAppenderUtils;
    }

    public void setSameSiteCookieAttributeAppenderUtils(SameSiteCookieAttributeAppenderUtils sameSiteCookieAttributeAppenderUtils) {
        this.sameSiteCookieAttributeAppenderUtils = sameSiteCookieAttributeAppenderUtils;
    }
}