package com.adyen.cockpit.setup.dto;

/** A base site of a store; its uid is the last path segment of the store's webhook URL. */
public record SiteView(String uid, String name)
{
}
