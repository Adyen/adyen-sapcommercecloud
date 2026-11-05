package com.adyen.v6.service;

import com.adyen.model.checkout.CheckoutVoucherAction;
import com.adyen.model.checkout.PaymentResponseAction;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class DefaultAdyenVoucherService implements AdyenVoucherService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenVoucherService.class);

    private ModelService modelService;

    @Override
    public void storeVoucherExpiry(final OrderModel order, final PaymentResponseAction action) {
        if (order == null) {
            return;
        }
        final CheckoutVoucherAction voucherAction = extractVoucherAction(action);
        if (voucherAction == null) {
            return;
        }
        final Date expiresAt = parseExpiresAt(voucherAction.getExpiresAt(), order.getCode());
        if (expiresAt == null) {
            return;
        }
        order.setAdyenVoucherExpiresAt(expiresAt);
        modelService.save(order);
    }

    protected CheckoutVoucherAction extractVoucherAction(final PaymentResponseAction action) {
        if (action == null) {
            return null;
        }
        final Object instance = action.getActualInstance();
        return instance instanceof CheckoutVoucherAction ? (CheckoutVoucherAction) instance : null;
    }

    protected Date parseExpiresAt(final String expiresAt, final String orderCode) {
        if (StringUtils.isBlank(expiresAt)) {
            LOG.warn("Voucher action for order {} has no expiresAt, expiry not stored", orderCode);
            return null;
        }
        final String value = expiresAt.trim();
        try {
            return Date.from(OffsetDateTime.parse(value).toInstant());
        } catch (final DateTimeException e) {
            LOG.debug("expiresAt '{}' has no offset", value);
        }
        try {
            return Date.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC));
        } catch (final DateTimeException e) {
            LOG.debug("expiresAt '{}' is not a local date-time", value);
        }
        try {
            return Date.from(LocalDate.parse(value).plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC));
        } catch (final DateTimeException e) {
            LOG.warn("Cannot parse voucher expiresAt '{}' for order {}, expiry not stored", expiresAt, orderCode);
            return null;
        }
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(final ModelService modelService) {
        this.modelService = modelService;
    }
}
