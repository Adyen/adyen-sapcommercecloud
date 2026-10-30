<%@ page trimDirectiveWhitespaces="true" %>

<%@ taglib prefix="template"
           tagdir="/WEB-INF/tags/responsive/template" %>
<%@ taglib prefix="spring"
           uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fn"
           uri="http://java.sun.com/jsp/jstl/functions" %>

<spring:url
        value="/api/checkout/get-payment-status"
        var="paymentStatusUrl" />

<spring:url
        value="/checkout/orderConfirmation/{orderIdentifier}"
        var="orderConfirmationUrl">
    <spring:param
            name="orderIdentifier"
            value="${orderIdentifier}" />
</spring:url>

<template:page
        pageTitle="${pageTitle}"
        hideHeaderLinks="true">

    <jsp:attribute name="pageScripts">
        <script type="text/javascript">
            $(function () {
                const paymentPending = $("#payment-pending");

                const statusUrl =
                    paymentPending.attr("data-status-url");

                const confirmationUrl =
                    paymentPending.attr("data-confirmation-url");

                const safeConfirmationUrl =
                    getSafeRedirectUrl(confirmationUrl);

                const orderIdentifier =
                    paymentPending.attr("data-order-identifier");

                const pollingInterval = 10000;
                const maximumAttempts = 120;

                let attempts = 0;
                let pollingFinished = false;

                function showFinalStatus(elementId) {
                    pollingFinished = true;

                    $("#payment-pending-waiting").hide();
                    $(".payment-pending-result").hide();
                    $(elementId).show();
                }

                function scheduleNextCheck() {
                    if (!pollingFinished) {
                        window.setTimeout(
                            checkPaymentStatus,
                            pollingInterval
                        );
                    }
                }

                function handleStatus(status) {
                    switch (status) {
                        case "completed":
                            if (safeConfirmationUrl) {
                                pollingFinished = true;
                                window.location.replace(safeConfirmationUrl);
                            } else {
                                showFinalStatus("#payment-error");
                            }
                            break;

                        case "cancelled":
                            showFinalStatus("#payment-cancelled");
                            break;

                        case "rejected":
                            showFinalStatus("#payment-rejected");
                            break;

                        case "error":
                            showFinalStatus("#payment-error");
                            break;

                        case "unknown":
                            showFinalStatus("#payment-timeout");
                            break;

                        case "waiting":
                        default:
                            scheduleNextCheck();
                            break;
                    }
                }

                function checkPaymentStatus() {
                    attempts++;

                    if (attempts > maximumAttempts) {
                        showFinalStatus("#payment-timeout");
                        return;
                    }

                    $.ajax({
                        url: statusUrl,
                        type: "POST",
                        data: JSON.stringify(orderIdentifier),
                        contentType:
                            "application/json; charset=UTF-8",
                        dataType: "json",
                        headers: {
                            "CSRFToken": ACC.config.CSRFToken
                        },
                        cache: false
                    })
                        .done(function (status) {
                            handleStatus($.trim(status));
                        })
                        .fail(function () {

                            scheduleNextCheck();
                        });
                }


                function getSafeRedirectUrl(url) {
                    if (!url) {
                        return null;
                    }

                    try {
                        const parsedUrl = new URL(
                            url,
                            window.location.origin
                        );

                        const isSameOrigin =
                            parsedUrl.origin === window.location.origin;

                        const isHttpProtocol =
                            parsedUrl.protocol === "http:" ||
                            parsedUrl.protocol === "https:";

                        if (isSameOrigin && isHttpProtocol) {
                            return parsedUrl.href;
                        }
                    } catch (e) {
                        return null;
                    }

                    return null;
                }

                window.setTimeout(
                    checkPaymentStatus,
                    pollingInterval
                );
            });
        </script>
    </jsp:attribute>

    <jsp:body>
        <div
                id="payment-pending"
                class="checkout-success"
                data-order-identifier="${fn:escapeXml(orderIdentifier)}"
                data-status-url="${fn:escapeXml(paymentStatusUrl)}"
                data-confirmation-url="${fn:escapeXml(orderConfirmationUrl)}">

            <div class="checkout-success__body">

                <div class="checkout-success__body__headline">
                    <spring:theme
                            code="checkout.payment.pending.title" />
                </div>

                <p>
                    <spring:theme
                            code="text.account.order.orderNumberLabel" />

                    <strong>
                            ${fn:escapeXml(orderIdentifier)}
                    </strong>
                </p>

                <div
                        id="payment-pending-waiting"
                        class="payment-pending-inline"
                        role="status"
                        aria-live="polite">

                    <span
                            class="payment-pending-inline__spinner"
                            aria-hidden="true">
                    </span>

                    <span class="payment-pending-inline__message">
                        <spring:theme
                                code="checkout.payment.pending.message" />
                    </span>
                </div>

                <div
                        id="payment-cancelled"
                        class="payment-pending-result"
                        role="alert"
                        style="display: none;">

                    <div class="alert alert-warning">
                        <spring:theme
                                code="checkout.error.authorization.payment.cancelled" />
                    </div>
                </div>

                <div
                        id="payment-rejected"
                        class="payment-pending-result"
                        role="alert"
                        style="display: none;">

                    <div class="alert alert-danger">
                        <spring:theme
                                code="checkout.error.authorization.payment.refused" />
                    </div>
                </div>

                <div
                        id="payment-error"
                        class="payment-pending-result"
                        role="alert"
                        style="display: none;">

                    <div class="alert alert-danger">
                        <spring:theme
                                code="checkout.error.authorization.payment.error" />
                    </div>
                </div>

                <div
                        id="payment-timeout"
                        class="payment-pending-result"
                        role="alert"
                        style="display: none;">

                    <div class="alert alert-info">
                        <spring:theme
                                code="checkout.payment.pending.timeout" />
                    </div>
                </div>

            </div>
        </div>
    </jsp:body>

</template:page>