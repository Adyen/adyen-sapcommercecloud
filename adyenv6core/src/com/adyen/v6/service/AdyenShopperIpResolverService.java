package com.adyen.v6.service;

import javax.servlet.http.HttpServletRequest;

public interface AdyenShopperIpResolverService {

    String resolveShopperIp(HttpServletRequest request);

}
