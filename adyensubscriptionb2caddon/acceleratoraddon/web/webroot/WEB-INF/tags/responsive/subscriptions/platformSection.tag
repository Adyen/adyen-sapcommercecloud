<%--
    One billing platform's subscriptions and the controls that act on them.

    Rendered once per platform by accountSubscriptionsPage.jsp, one section under the other. A section reads
    nothing but its own data: its controls were built from its own platform's declarations, every form posts
    the section's platform so the server refuses a subscription from elsewhere, and element ids carry the
    platform code so two sections on one page never collide.

    One full-width row per subscription, because the content is not small - a name, a state sentence, two
    order lines, a card summary, and up to two controls. The state sentence is the payload and is always
    visible; the icon beside it repeats it and is therefore aria-hidden. Colour carries nothing on its own:
    a left rule and a glyph shade follow the state's tone, and every state also says what it is in words.

    Actions live in native <details> disclosures - no JavaScript, so the page is legible with scripts off -
    and a row with no actions gives its column back rather than leaving a hole beside it.

    Forms add the CSRF token by hand: this storefront configures its own CSRF parameter name, so Spring's
    form tag does not supply it, and POST is the only method the matcher checks.

    Message arguments go in <spring:argument>, never in the arguments attribute, which splits on commas: a long
    date such as "October 8, 2026" would lose its year.
--%>
<%@ tag body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ attribute name="section" required="true"
              type="com.adyen.commerce.connector.facades.data.SubscriptionPlatformSectionData" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="subscriptions" tagdir="/WEB-INF/tags/addons/adyensubscriptionb2caddon/responsive/subscriptions" %>

<%-- One glyph per state. A map rather than a method on the state: these names belong to the storefront's icon
     font. A state missing from this map renders without an icon. --%>
<c:set var="subxGlyphs" value=",SETTING_UP=cog,ACTIVE=refresh,STARTING_SOON=calendar,ENDING=time,PAUSED=pause,PAST_DUE=exclamation-sign,PAST_DUE_ENDING=exclamation-sign,ENDED=remove-circle,UNAVAILABLE=question-sign"/>
<c:set var="platform" value="${fn:escapeXml(section.platformCode)}"/>
<c:set var="idPrefix" value="subx-${platform}"/>
<c:url value="/my-account/subscriptions/payment-method" var="paymentMethodUrl"/>
<c:url value="/my-account/subscriptions/cancel" var="cancelUrl"/>
<%-- The store is named only when this platform has more than one account here, which happens when the shopper
     bought in several stores selling on it. --%>
<c:set var="nameAccounts"
       value="${fn:length(section.paymentMethodChangeOffers) gt 1 or fn:length(section.paymentMethodEnrollmentOffers) gt 1}"/>

<section class="subx-section" aria-labelledby="${idPrefix}-title">

    <%-- The name comes from the platform's connector extension (its BillingPlatform value localization). --%>
    <h2 class="subx-section-title" id="${idPrefix}-title">${fn:escapeXml(section.platformName)}</h2>

    <%-- Orders paid for that never became a subscription. Shown above the list rather than instead of it: this
         shopper may also have subscriptions that worked. --%>
    <c:if test="${not empty section.ordersAwaitingSetup}">
        <div class="subx-notice" role="alert">
            <p class="subx-notice-title"><spring:theme code="text.account.subscriptions.awaitingSetup.title"/></p>
            <ul>
                <c:forEach items="${section.ordersAwaitingSetup}" var="orderCode">
                    <li><spring:theme code="text.account.subscriptions.awaitingSetup"><spring:argument value="${fn:escapeXml(orderCode)}"/></spring:theme></li>
                </c:forEach>
            </ul>
        </div>
    </c:if>

    <%-- Account-wide control: where the platform changes the method for the whole account, one control per
         account rather than one per row, which would repeat the same promise on every row. --%>
    <c:forEach items="${section.paymentMethodChangeOffers}" var="offer" varStatus="offerStatus">
        <c:set var="offerId" value="${idPrefix}-account-${offerStatus.index}"/>
        <form:form action="${paymentMethodUrl}" method="post" id="${offerId}" cssClass="subx-panel">
            <input type="hidden" name="${CSRFToken.parameterName}" value="${CSRFToken.token}"/>
            <input type="hidden" name="platform" value="${platform}"/>
            <input type="hidden" name="code" value="${fn:escapeXml(offer.subscriptionCode)}"/>
            <div class="subx-panel-title">
                <spring:theme code="text.account.subscriptions.paymentMethod"/>
            </div>
            <c:if test="${nameAccounts and not empty offer.accountName}">
                <p class="subx-note subx-note--first">
                    <spring:theme code="text.account.subscriptions.paymentMethod.account" htmlEscape="true">
                        <spring:argument value="${offer.accountName}"/>
                    </spring:theme>
                </p>
            </c:if>
            <div class="subx-panel-fields">
                <div class="subx-field">
                    <label class="subx-label" for="${offerId}-method">
                        <spring:theme code="text.account.subscriptions.paymentMethod.choose"/>
                    </label>
                    <select name="storedPaymentMethodId" id="${offerId}-method" class="form-control subx-select">
                        <subscriptions:paymentMethodOptions platformOptions="${offer.paymentMethodOptions}"
                                                            vaultOptions="${offer.adyenVaultOptions}"/>
                    </select>
                </div>
                <button type="submit" class="btn btn-default subx-btn">
                    <spring:theme code="text.account.subscriptions.paymentMethod.submit"/>
                </button>
            </div>
            <p class="subx-note">
                <spring:theme code="text.account.subscriptions.paymentMethod.note.account"/>
            </p>
        </form:form>
    </c:forEach>

    <%-- The provider's own page for taking a new card. A link, not a form: nothing changes here, and the address
         is resolved by the controller so whatever token it carries never reaches this markup. Opens in a new tab
         because the provider does not send the shopper back. --%>
    <c:forEach items="${section.paymentMethodEnrollmentOffers}" var="enrollment">
        <div class="subx-panel subx-panel--quiet">
            <div class="subx-panel-title">
                <spring:theme code="text.account.subscriptions.paymentMethod.add"/>
            </div>
            <c:if test="${nameAccounts and not empty enrollment.accountName}">
                <p class="subx-note subx-note--first">
                    <spring:theme code="text.account.subscriptions.paymentMethod.account" htmlEscape="true">
                        <spring:argument value="${enrollment.accountName}"/>
                    </spring:theme>
                </p>
            </c:if>
            <p class="subx-note">
                <spring:theme code="text.account.subscriptions.paymentMethod.add.effect.${enrollment.notice}"/>
            </p>
            <c:url value="/my-account/subscriptions/payment-method/add" var="enrollmentUrl">
                <c:param name="platform" value="${section.platformCode}"/>
                <c:param name="code" value="${enrollment.subscriptionCode}"/>
            </c:url>
            <a class="btn btn-default subx-btn" target="_blank" rel="noopener noreferrer"
               href="${fn:escapeXml(enrollmentUrl)}">
                <spring:theme code="text.account.subscriptions.paymentMethod.add.submit"/>
            </a>
        </div>
    </c:forEach>

    <%-- Said once, and only when this platform cannot change a payment method here at all. Keyed off what the
         platform can do, not off what is changeable today: a subscription bought minutes ago is not confirmed
         yet, which is not the same as a provider that cannot change cards. --%>
    <c:if test="${not section.paymentMethodChangeSupported and not empty section.subscriptions}">
        <p class="subx-note subx-note--standalone">
            <spring:theme code="text.account.subscriptions.paymentMethod.unsupported"/>
        </p>
    </c:if>

    <c:if test="${not empty section.subscriptions}">
        <div class="subx-list">
            <c:forEach items="${section.subscriptions}" var="subscription" varStatus="row">
                <c:set var="rowId" value="${idPrefix}-${row.index}"/>

                <%-- Decided before the row is opened so the row can collapse to full width when nothing
                     applies. "Unavailable" is said only where this section offers the change somewhere, the
                     state would make a working card matter, and no control here moves this row. --%>
                <c:set var="showRowUnavailable"
                       value="${section.paymentMethodChangeOffered
                                and subscription.state.paymentMethodChangeable
                                and not subscription.paymentMethodChangeCovered}"/>
                <c:set var="hasActions"
                       value="${subscription.cancellable or subscription.paymentMethodControl or showRowUnavailable}"/>

                <div class="subx-row subx-row--${subscription.state.tone}${hasActions ? '' : ' subx-row--noactions'}">

                    <div class="subx-row-body">
                        <div class="subx-name">
                            <c:choose>
                                <c:when test="${not empty subscription.productName}">
                                    ${fn:escapeXml(subscription.productName)}
                                </c:when>
                                <c:otherwise>
                                    <spring:theme code="text.account.subscriptions.unnamed"/>
                                </c:otherwise>
                            </c:choose>
                            <c:if test="${subscription.quantity gt 1}">
                                <span class="subx-qty">&times;&nbsp;${fn:escapeXml(subscription.quantity)}</span>
                            </c:if>
                        </div>

                        <%-- The state's own sentence. The key carries ".dated" only when a date is actually
                             known, so nothing ever renders an empty "until". --%>
                        <p class="subx-state">
                            <c:set var="subxGlyphKey" value=",${subscription.state}="/>
                            <c:set var="subxGlyph"
                                   value="${fn:substringBefore(fn:substringAfter(subxGlyphs, subxGlyphKey), ',')}"/>
                            <c:if test="${not empty subxGlyph}">
                                <span class="glyphicon glyphicon-${subxGlyph}" aria-hidden="true"></span>
                            </c:if>
                            <c:set var="stateKey"
                                   value="text.account.subscriptions.state.${fn:toLowerCase(subscription.state)}"/>
                            <c:choose>
                                <c:when test="${not empty subscription.effectiveDate}">
                                    <fmt:formatDate value="${subscription.effectiveDate}"
                                                    dateStyle="long" var="effectiveDate"/>
                                    <spring:theme code="${stateKey}.dated"><spring:argument value="${effectiveDate}"/></spring:theme>
                                </c:when>
                                <c:otherwise>
                                    <spring:theme code="${stateKey}"/>
                                </c:otherwise>
                            </c:choose>
                        </p>

                        <%-- Order provenance. It names the card the order was paid with rather than the one
                             billing uses now, so after any change it still shows the old one. --%>
                        <p class="subx-meta">
                            <c:if test="${not empty subscription.orderDate}">
                                <fmt:formatDate value="${subscription.orderDate}" dateStyle="long"
                                                var="orderedOn"/>
                                <span><spring:theme code="text.account.subscriptions.order"><spring:argument value="${orderedOn}"/></spring:theme></span>
                            </c:if>
                            <c:if test="${not empty subscription.orderCode}">
                                <span><spring:theme code="text.account.subscriptions.orderNumber"><spring:argument value="${fn:escapeXml(subscription.orderCode)}"/></spring:theme></span>
                            </c:if>
                            <c:if test="${not empty subscription.paymentMethodSummary}">
                                <span><spring:theme code="text.account.subscriptions.paidWith"><spring:argument value="${fn:escapeXml(subscription.paymentMethodSummary)}"/></spring:theme></span>
                            </c:if>
                        </p>
                    </div>

                    <c:if test="${hasActions}">
                        <div class="subx-row-actions">

                            <%-- Per row, only where the platform pins the method to one subscription. Open
                                 already on the states where the card IS the problem. --%>
                            <c:if test="${subscription.paymentMethodControl}">
                                <ycommerce:testId code="subscription_payment_method_row">
                                    <details class="subx-disclosure"
                                             ${subscription.state.tone eq 'attention' ? 'open' : ''}>
                                        <summary class="subx-summary">
                                            <spring:theme code="text.account.subscriptions.paymentMethod.row"/>
                                        </summary>
                                        <div class="subx-disclosure-body">
                                            <form:form action="${paymentMethodUrl}" method="post"
                                                       id="${rowId}-method-form">
                                                <input type="hidden" name="${CSRFToken.parameterName}"
                                                       value="${CSRFToken.token}"/>
                                                <input type="hidden" name="platform" value="${platform}"/>
                                                <input type="hidden" name="code"
                                                       value="${fn:escapeXml(subscription.code)}"/>
                                                <label class="subx-label" for="${rowId}-method">
                                                    <spring:theme code="text.account.subscriptions.paymentMethod.choose.row"/>
                                                </label>
                                                <select name="storedPaymentMethodId" id="${rowId}-method"
                                                        class="form-control subx-select">
                                                    <subscriptions:paymentMethodOptions
                                                            platformOptions="${subscription.paymentMethodOptions}"
                                                            vaultOptions="${subscription.adyenVaultOptions}"/>
                                                </select>
                                                <p class="subx-note">
                                                    <spring:theme code="text.account.subscriptions.paymentMethod.note.subscription"/>
                                                </p>
                                                <%-- Only where another subscription on the same account could
                                                     move too; each is pinned separately, so moving them all
                                                     means touching each. --%>
                                                <c:if test="${subscription.paymentMethodShareable}">
                                                    <label class="subx-check" for="${rowId}-all">
                                                        <input type="checkbox" id="${rowId}-all"
                                                               name="applyToAll" value="true"/>
                                                        <spring:theme code="text.account.subscriptions.paymentMethod.applyToAll"/>
                                                    </label>
                                                </c:if>
                                                <button type="submit" class="btn btn-default subx-btn">
                                                    <spring:theme code="text.account.subscriptions.paymentMethod.row.submit"/>
                                                </button>
                                            </form:form>
                                        </div>
                                    </details>
                                </ycommerce:testId>
                            </c:if>

                            <c:if test="${showRowUnavailable}">
                                <p class="subx-note subx-note--row">
                                    <spring:theme code="text.account.subscriptions.paymentMethod.row.unavailable"/>
                                </p>
                            </c:if>

                            <%-- Last, quietest, and still a labelled 44px control. The note is inside the
                                 disclosure so it is read immediately before the commit. --%>
                            <c:if test="${subscription.cancellable}">
                                <ycommerce:testId code="subscription_cancel_button">
                                    <details class="subx-disclosure">
                                        <summary class="subx-summary subx-summary--destructive">
                                            <spring:theme code="text.account.subscriptions.cancel"/>
                                        </summary>
                                        <div class="subx-disclosure-body">
                                            <p class="subx-note subx-note--first">
                                                <spring:theme code="text.account.subscriptions.cancel.note"/>
                                            </p>
                                            <form:form action="${cancelUrl}" method="post" id="${rowId}-cancel-form">
                                                <input type="hidden" name="${CSRFToken.parameterName}"
                                                       value="${CSRFToken.token}"/>
                                                <input type="hidden" name="platform" value="${platform}"/>
                                                <input type="hidden" name="code"
                                                       value="${fn:escapeXml(subscription.code)}"/>
                                                <button type="submit" class="btn btn-default subx-btn subx-btn--quiet">
                                                    <spring:theme code="text.account.subscriptions.cancel"/>
                                                </button>
                                            </form:form>
                                        </div>
                                    </details>
                                </ycommerce:testId>
                            </c:if>

                        </div>
                    </c:if>
                </div>
            </c:forEach>
        </div>
    </c:if>
</section>
