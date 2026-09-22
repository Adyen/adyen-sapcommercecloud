package com.adyen.v6.service;

import jakarta.servlet.http.HttpServletRequest;

public interface AdyenShopperIpResolverService {

    String resolveShopperIp(HttpServletRequest request);

}
