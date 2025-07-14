/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.backoffice.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


/**
 * Main controller for handling page requests
 */
@Controller
public class MainController
{
    /**
     * Handle dashboard page requests - serve the Next.js app
     */
    @GetMapping("/")
    public String dashboard(HttpServletRequest request, Model model)
    {
        // Check if user is authenticated
        if (isUserAuthenticated(request))
        {
            // User is authenticated, serve the Next.js app
            return "index";
        }
        
        // User is not authenticated, redirect to login
        return "redirect:/login";
    }
    
    /**
     * Check if user is authenticated using both session and Spring Security context
     */
    private boolean isUserAuthenticated(HttpServletRequest request)
    {
        // First check session attribute
        Boolean sessionAuthenticated = (Boolean) request.getSession().getAttribute("authenticated");
        if (sessionAuthenticated != null && sessionAuthenticated)
        {
            return true;
        }
        
        // Then check Spring Security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() &&
               !auth.getName().equals("anonymousUser") &&
               !"anonymousUser".equals(auth.getPrincipal());
    }

}