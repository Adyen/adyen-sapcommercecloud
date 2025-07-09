/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.backoffice.service.impl;

import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.user.UserService;
// import de.hybris.platform.servicelayer.security.permissions.PermissionService;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.adyen.backoffice.service.AdyenAuthenticationService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.annotation.Resource;
import java.security.Key;
import java.util.function.Function;

/**
 * Default implementation of {@link AdyenAuthenticationService}
 */
@Service
public class DefaultAdyenAuthenticationService implements AdyenAuthenticationService
{
    private static final String ADYEN_BACKOFFICE_PERMISSION = "adyen_backoffice_access";
    private static final long JWT_TOKEN_VALIDITY = 5 * 60 * 60; // 5 hours
    
    private Key key = Keys.secretKeyFor(SignatureAlgorithm.HS512);

    @Resource(name = "userService")
    private UserService userService;


    @Override
    public Map<String, Object> authenticate( final UserModel user)
    {
        final Map<String, Object> result = new HashMap<>();
        result.put("success", false);

        try
        {
            final String token = generateToken(user.getName());
            result.put("success", true);
            result.put("token", token);
            result.put("uid", user.getUid());
            result.put("name", user.getName());

        }
        catch (UnknownIdentifierException e)
        {
            // User not found, authentication fails
        }

        return result;
    }
    
    @Override
    public boolean validateToken(String token)
    {
        if (StringUtils.isBlank(token))
        {
            return false;
        }
        
        try
        {
            final String username = getUsernameFromToken(token);
            return !isTokenExpired(token) && hasAdyenBackofficePermission(username);
        }
        catch (Exception e)
        {
            return false;
        }
    }
    
    @Override
    public String generateToken(String username)
    {
        final Map<String, Object> claims = new HashMap<>();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY * 1000))
                .signWith(key)
                .compact();
    }
    
    @Override
    public String getUsernameFromToken(String token)
    {
        return getClaimFromToken(token, Claims::getSubject);
    }
    
    @Override
    public boolean hasAdyenBackofficePermission(String username)
    {
        try
        {
            final UserModel user = getUserService().getUserForUID(username);
            return true;//permissionService.checkPermission(user, ADYEN_BACKOFFICE_PERMISSION);
        }
        catch (UnknownIdentifierException e)
        {
            return false;
        }
    }
    
    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver)
    {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims getAllClaimsFromToken(String token)
    {
        return Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    private Boolean isTokenExpired(String token)
    {
        final Date expiration = getClaimFromToken(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    public UserService getUserService() {
        return userService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

}