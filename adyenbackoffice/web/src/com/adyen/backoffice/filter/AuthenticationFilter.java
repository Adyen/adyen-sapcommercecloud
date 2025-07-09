/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.backoffice.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.adyen.backoffice.service.AdyenAuthenticationService;

/**
 * Filter for authenticating requests to protected endpoints
 */
@Component
public class AuthenticationFilter extends OncePerRequestFilter
{
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    // List of paths that don't require authentication
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/validate",
            "/static/",
            "/favicon.ico"
    );
    
    @Resource(name = "adyenAuthenticationService")
    private AdyenAuthenticationService authenticationService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        final String requestPath = request.getRequestURI();
        
        // Skip authentication for public paths
        if (isPublicPath(requestPath))
        {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check for Authorization header
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith(BEARER_PREFIX))
        {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        
        // Extract and validate token
        final String token = authHeader.substring(BEARER_PREFIX.length());
        
        if (authenticationService.validateToken(token))
        {
            // Token is valid, proceed with the request
            filterChain.doFilter(request, response);
        }
        else
        {
            // Token is invalid, return 401 Unauthorized
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        }
    }
    
    private boolean isPublicPath(String requestPath)
    {
        return PUBLIC_PATHS.stream().anyMatch(path -> {
            if (path.endsWith("/"))
            {
                return requestPath.startsWith(path);
            }
            return requestPath.equals(path);
        });
    }
}