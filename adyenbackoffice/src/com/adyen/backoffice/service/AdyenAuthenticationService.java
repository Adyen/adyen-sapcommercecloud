/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.backoffice.service;

import de.hybris.platform.core.model.user.UserModel;

import java.util.Map;

/**
 * Service for handling authentication with SAP Commerce Backoffice
 */
public interface AdyenAuthenticationService
{
    /**
     * Authenticates a user against SAP Commerce Backoffice
     *
     * @return a map containing authentication result and user details if successful
     */
    Map<String, Object> authenticate(final UserModel user);

    /**
     * Validates if a JWT token is valid
     * 
     * @param token the JWT token
     * @return true if the token is valid, false otherwise
     */
    boolean validateToken(String token);
    
    /**
     * Generates a JWT token for an authenticated user
     * 
     * @param username the username
     * @return the generated JWT token
     */
    String generateToken(String username);
    
    /**
     * Extracts the username from a JWT token
     * 
     * @param token the JWT token
     * @return the username
     */
    String getUsernameFromToken(String token);
    
    /**
     * Checks if a user has permission to access the Adyen Backoffice
     * 
     * @param username the username
     * @return true if the user has permission, false otherwise
     */
    boolean hasAdyenBackofficePermission(String username);
}