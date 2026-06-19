/*
 * [y] hybris Platform
 *
 * Copyright (c) 2017 SAP SE or an SAP affiliate company.  All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package com.adyen.v6.controller;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.v6.security.AdyenNotificationAuthenticationProvider;
import com.adyen.v6.service.AdyenNotificationService;
import com.adyen.v6.service.AdyenNotificationV2Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;


@Controller
@RequestMapping(value = "/adyen/v6/notification/{baseSiteId}")
public class AdyenNotificationControllerV2 {
	private static final Logger LOG = Logger.getLogger(AdyenNotificationControllerV2.class);
	@Resource
	@Lazy
	private AdyenNotificationV2Service adyenNotificationV2Service;

	@Resource(name = "adyenNotificationAuthenticationProvider")
	@Lazy
	private AdyenNotificationAuthenticationProvider adyenNotificationAuthenticationProvider;

	@Resource(name = "adyenNotificationService")
	@Lazy
	private AdyenNotificationService adyenNotificationService;

	private static final String RESPONSE_ACCEPTED = "[accepted]";
	private static final String RESPONSE_NOT_ACCEPTED = "[not-accepted]";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String ADDITIONAL_DATA = "additionalData";
	private static final String REDACTION_FAILED = "[unable-to-redact-notification-payload]";

	@RequestMapping(value = "/json", method = RequestMethod.POST)
	@ResponseBody
	public String onReceive(@PathVariable final String baseSiteId, final HttpServletRequest request) {
		String requestString = null;
		NotificationRequest notificationRequest = null;
		try {
			//Parse response body by request input stream so that Spring doesn't try to deserialize
			requestString = IOUtils.toString(request.getInputStream());
			notificationRequest = adyenNotificationService.getNotificationRequestFromString(requestString);
		} catch (IOException e) {
			LOG.error(e);
			return RESPONSE_NOT_ACCEPTED;
		}

		if (notificationRequest == null) {
			LOG.error("Notification request parsing failure");
			return RESPONSE_NOT_ACCEPTED;
		}

		if (!adyenNotificationAuthenticationProvider.authenticate(request, notificationRequest, baseSiteId)) {
			return RESPONSE_NOT_ACCEPTED;
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("Received Adyen notification:" + redactSensitiveData(requestString));
		}

		adyenNotificationV2Service.onRequest(notificationRequest);

		return RESPONSE_ACCEPTED;
    }

	private String redactSensitiveData(final String requestString) {
		if (requestString == null || requestString.isEmpty()) {
			return requestString;
		}

		try {
			final JsonNode root = OBJECT_MAPPER.readTree(requestString);

			root.findParents(ADDITIONAL_DATA).forEach(parent -> {
				if (parent instanceof ObjectNode objectNode) {
					objectNode.set(ADDITIONAL_DATA, OBJECT_MAPPER.createObjectNode());
				}
			});

			return OBJECT_MAPPER.writeValueAsString(root);
		} catch (final JsonProcessingException e) {
			return REDACTION_FAILED;
		}
	}
}
