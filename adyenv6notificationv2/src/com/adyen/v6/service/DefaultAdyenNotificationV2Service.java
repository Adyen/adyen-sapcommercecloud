package com.adyen.v6.service;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.v6.events.AbstractNotificationEvent;
import com.adyen.v6.events.builder.*;
import com.adyen.v6.model.AdyenNotificationModel;
import com.google.gson.Gson;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;


public class DefaultAdyenNotificationV2Service implements AdyenNotificationV2Service {
    private static final org.apache.log4j.Logger LOG = Logger.getLogger(DefaultAdyenNotificationV2Service.class);

    private ModelService modelService;
    private EventService eventService;
    private Map<String, AbstractNotificationEventBuilder> eventTemplateMap;


    {
        eventTemplateMap = new HashMap<>();
        eventTemplateMap.put(NotificationRequestItem.EVENT_CODE_AUTHORISATION, new AuthorisationEventBuilder());
        eventTemplateMap.put(NotificationRequestItem.EVENT_CODE_CANCEL_OR_REFUND, new CancelOrRefundEventBuilder());
        eventTemplateMap.put(NotificationRequestItem.EVENT_CODE_CAPTURE, new CaptureEventBuilder());
        eventTemplateMap.put(NotificationRequestItem.EVENT_CODE_OFFER_CLOSED, new OfferClosedEventBuilder());
        eventTemplateMap.put(NotificationRequestItem.EVENT_CODE_REFUND, new RefundEventBuilder());
    }

    @Override
    public void onRequest(final NotificationRequest notificationRequest) {
        for (NotificationRequestItem item : notificationRequest.getNotificationItems()) {
            try {
                AdyenNotificationModel notificationModel = save(item);
                Optional<AbstractNotificationEvent> optionalEvent = createEvent(notificationModel);
                optionalEvent.ifPresent(this::publish);
            } catch (Exception e) {
                LOG.error("NotificationRequestItem processing failed.", e);
            }
        }
    }

    protected AdyenNotificationModel save(NotificationRequestItem notificationRequestItem) {
        AdyenNotificationModel adyenNotificationModel = populate(notificationRequestItem);
        modelService.save(adyenNotificationModel);
        return adyenNotificationModel;
    }

    protected AdyenNotificationModel populate(NotificationRequestItem source) {

        AdyenNotificationModel target = new AdyenNotificationModel();
        Gson gson = new Gson();
        if (source.getAmount() != null) {
            target.setAmountCurrency(source.getAmount().getCurrency());
            target.setAmountValue(source.getAmount().getDecimalValue());
        }

        target.setUuid(UUID.randomUUID().toString());
        target.setEventCode(source.getEventCode());
        target.setEventDate(source.getEventDate());
        target.setMerchantAccountCode(source.getMerchantAccountCode());
        target.setMerchantReference(source.getMerchantReference());
        target.setOriginalReference(source.getOriginalReference());
        target.setPspReference(source.getPspReference());
        target.setReason(source.getReason());
        target.setSuccess(source.isSuccess());
        target.setPaymentMethod(source.getPaymentMethod());

        String additionalDataJson = gson.toJson(source.getAdditionalData());
        target.setAdditionalData(additionalDataJson);

        target.setCreatedAt(new Date());

        return target;
    }

    protected Optional<AbstractNotificationEvent> createEvent(AdyenNotificationModel adyenNotificationModel) {
        if (StringUtils.isNotEmpty(adyenNotificationModel.getEventCode())) {
            AbstractNotificationEventBuilder eventTemplate = eventTemplateMap.get(adyenNotificationModel.getEventCode());
            if (eventTemplate == null) {
                LOG.warn("Notification type " + adyenNotificationModel.getEventCode() + " not supported!");
                return Optional.empty();
            }
            return Optional.of(eventTemplate.buildEvent(adyenNotificationModel));
        } else {
            LOG.error("Event code is empty!");
            throw new IllegalArgumentException("Event code is empty!");
        }

    }


    protected void publish(AbstractNotificationEvent event) {
        getEventService().publishEvent(event);
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(final ModelService modelService) {
        this.modelService = modelService;
    }

    public EventService getEventService() {
        return eventService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }
}
