<%--
    The "My subscriptions" page body.

    A fragment, not a page: it is rendered inside a ContentSlot by a JspIncludeComponent, so there is no
    <template:page> here. Wrapping it in one produces a page inside a page, or nothing at all.

    One full-width row per subscription, because the content is not small - a name, a state sentence, two
    order lines, a card summary, and up to two controls - and a card grid forces that either into a
    scrollbox or behind a disclosure nobody opens. The state sentence is the payload and is always visible;
    the icon beside it repeats it and is therefore aria-hidden.

    Colour carries nothing on its own. A left rule and a glyph shade follow SubscriptionDisplayState.tone(),
    and every state also says what it is in words, so a shopper who cannot see colour reads the same page.

    Actions live in native <details> disclosures - no JavaScript, so the page is legible with scripts off -
    and a row with no actions gives its column back rather than leaving a hole beside it.
--%>
<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>

<%-- One glyph per state. A map rather than a method on the enum: these names belong to the storefront's
     icon font, and the enum lives in the vendor-neutral core, which has no business knowing them. A state
     missing from this map simply renders without an icon. --%>
<c:set var="subxGlyphs" value=",SETTING_UP=cog,ACTIVE=refresh,STARTING_SOON=calendar,ENDING=time,PAUSED=pause,PAST_DUE=exclamation-sign,PAST_DUE_ENDING=exclamation-sign,ENDED=remove-circle,UNAVAILABLE=question-sign"/>

<div class="subx">

    <div class="account-section-header">
        <spring:theme code="text.account.subscriptions"/>
    </div>

    <%-- Orders paid for that never became a subscription. One block with a line each, not one block per
         order, and shown above the list rather than instead of it: this shopper may also have subscriptions
         that worked. --%>
    <c:if test="${not empty ordersAwaitingSetup}">
        <div class="subx-notice" role="alert">
            <p class="subx-notice-title"><spring:theme code="text.account.subscriptions.awaitingSetup.title"/></p>
            <ul>
                <c:forEach items="${ordersAwaitingSetup}" var="orderCode">
                    <li><spring:theme code="text.account.subscriptions.awaitingSetup"
                                      arguments="${fn:escapeXml(orderCode)}"/></li>
                </c:forEach>
            </ul>
        </div>
    </c:if>

    <%-- One flag for "somewhere on this page there is a control the shopper can press", and it has to be
         the union of the two kinds. The control above the list needs the Adyen vault; a row's own control
         needs its platform to hold a second method and knows nothing about the vault. --%>
    <c:set var="changeOfferedSomewhere"
           value="${(not empty paymentMethodSubscriptionCode and not empty storedCards)
                    or anyRowPaymentMethodControl}"/>

    <%-- The control above the list belongs to a customer-scoped platform, where the change moves every
         subscription that platform bills. A control per row would be that promise repeated once per row. --%>
    <c:if test="${not empty paymentMethodSubscriptionCode and not empty storedCards}">
        <form:form action="${request.contextPath}/my-account/subscriptions/payment-method" method="post"
                   id="subscriptionPaymentMethodAll" cssClass="subx-panel">
            <input type="hidden" name="${CSRFToken.parameterName}" value="${CSRFToken.token}"/>
            <%-- Chosen by the facade: a row whose connector offers the change, in a state where it means
                 something, carrying a public identifier. "First on screen" satisfies none of those. --%>
            <input type="hidden" name="code" value="${fn:escapeXml(paymentMethodSubscriptionCode)}"/>
            <div class="subx-panel-title">
                <spring:theme code="text.account.subscriptions.paymentMethod"/>
            </div>
            <div class="subx-panel-fields">
                <div class="subx-field">
                    <label class="subx-label" for="subxCardAll">
                        <spring:theme code="text.account.subscriptions.paymentMethod.choose"/>
                    </label>
                    <select name="storedPaymentMethodId" id="subxCardAll" class="form-control subx-select">
                        <c:forEach items="${storedCards}" var="storedCard">
                            <option value="${fn:escapeXml(storedCard.id)}">
                                ${fn:escapeXml(storedCard.brand)}&nbsp;&bull;&bull;&bull;&bull;&nbsp;${fn:escapeXml(storedCard.lastFour)}
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <button type="submit" class="btn btn-default subx-btn">
                    <spring:theme code="text.account.subscriptions.paymentMethod.submit"/>
                </button>
            </div>
            <p class="subx-note">
                <spring:theme code="text.account.subscriptions.paymentMethod.note.${paymentMethodChangeScope}"/>
            </p>
        </form:form>
    </c:if>

    <%-- The provider's own page for taking a new card. A link, not a form: nothing changes here, and the
         address is resolved by the controller so the token it carries never reaches this markup. Opens in a
         new tab because the provider does not send the shopper back, and they need this page afterwards to
         choose which subscription uses the card. --%>
    <c:if test="${not empty paymentMethodEnrollmentSubscriptionCode}">
        <div class="subx-panel subx-panel--quiet">
            <div class="subx-panel-title">
                <spring:theme code="text.account.subscriptions.paymentMethod.add"/>
            </div>
            <p class="subx-note">
                <spring:theme code="text.account.subscriptions.paymentMethod.add.effect.${paymentMethodEnrollmentEffect}"/>
            </p>
            <a class="btn btn-default subx-btn"
               target="_blank" rel="noopener noreferrer"
               href="<c:url value="/my-account/subscriptions/payment-method/add"><c:param name="code" value="${paymentMethodEnrollmentSubscriptionCode}"/></c:url>">
                <spring:theme code="text.account.subscriptions.paymentMethod.add.submit"/>
            </a>
        </div>
    </c:if>

    <%-- Said once, and only when no provider on this page can do it at all. It keys off
         paymentMethodChangeSupportedSomewhere and NOT off what is changeable today: a subscription bought
         minutes ago has not been confirmed by its platform yet, which is not the same as a provider that
         cannot change cards at all. --%>
    <c:if test="${not paymentMethodChangeSupportedSomewhere and not empty subscriptions}">
        <p class="subx-note subx-note--standalone">
            <spring:theme code="text.account.subscriptions.paymentMethod.unsupported"/>
        </p>
    </c:if>

    <c:choose>
        <c:when test="${not empty subscriptions}">
            <div class="subx-list">
                <c:forEach items="${subscriptions}" var="subscription" varStatus="row">

                    <%-- Both halves of the actions column, decided before the row is opened so the row can collapse
                         to full width when neither applies. Two sources may feed the control: what the billing
                         account already holds, and cards from the shopper's Adyen vault where the platform accepts
                         an import. Either one alone is enough; neither means no control. --%>
                    <c:set var="showRowCardChange"
                           value="${subscription.paymentMethodChangeable
                                    and subscription.paymentMethodChangeScope eq 'SUBSCRIPTION'
                                    and (not empty subscription.paymentMethodOptions
                                         or not empty subscription.adyenVaultOptions)}"/>
                    <c:set var="showRowUnavailable"
                           value="${changeOfferedSomewhere
                                    and subscription.state.paymentMethodChangeable
                                    and not subscription.paymentMethodChangeCovered}"/>
                    <c:set var="hasActions"
                           value="${subscription.cancellable or showRowCardChange or showRowUnavailable}"/>

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

                            <%-- The state's own sentence. The key carries ".dated" only when a date is
                                 actually known, so nothing ever renders an empty "until". --%>
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
                                        <spring:theme code="${stateKey}.dated" arguments="${effectiveDate}"/>
                                    </c:when>
                                    <c:otherwise>
                                        <spring:theme code="${stateKey}"/>
                                    </c:otherwise>
                                </c:choose>
                            </p>

                            <%-- Order provenance, grouped on one line so the card summary is unmistakably
                                 the ORDER's card. It names the card the order was paid with rather than the
                                 one billing uses now, so after any change it still shows the old one. --%>
                            <p class="subx-meta">
                                <c:if test="${not empty subscription.orderDate}">
                                    <fmt:formatDate value="${subscription.orderDate}" dateStyle="long"
                                                    var="orderedOn"/>
                                    <span><spring:theme code="text.account.subscriptions.order"
                                                        arguments="${orderedOn}"/></span>
                                </c:if>
                                <c:if test="${not empty subscription.orderCode}">
                                    <span><spring:theme code="text.account.subscriptions.orderNumber"
                                                        arguments="${fn:escapeXml(subscription.orderCode)}"/></span>
                                </c:if>
                                <c:if test="${not empty subscription.paymentMethodSummary}">
                                    <span><spring:theme code="text.account.subscriptions.paidWith"
                                                        arguments="${fn:escapeXml(subscription.paymentMethodSummary)}"/></span>
                                </c:if>
                            </p>
                        </div>

                        <c:if test="${hasActions}">
                            <div class="subx-row-actions">

                                <%-- Per row, and only where the platform pins the method to one subscription. Open
                                     already on the two states where the card IS the problem: one extra tap between the
                                     shopper and the fix is the wrong economy there. --%>
                                <c:if test="${showRowCardChange}">
                                    <ycommerce:testId code="subscription_payment_method_row">
                                        <details class="subx-disclosure"
                                                 ${subscription.state.tone eq 'attention' ? 'open' : ''}>
                                            <summary class="subx-summary">
                                                <spring:theme code="text.account.subscriptions.paymentMethod.row"/>
                                            </summary>
                                            <div class="subx-disclosure-body">
                                                <form:form action="${request.contextPath}/my-account/subscriptions/payment-method"
                                                           method="post" id="subscriptionPaymentMethod-${row.index}">
                                                    <input type="hidden" name="${CSRFToken.parameterName}"
                                                           value="${CSRFToken.token}"/>
                                                    <input type="hidden" name="code"
                                                           value="${fn:escapeXml(subscription.code)}"/>
                                                    <label class="subx-label" for="subxCard-${row.index}">
                                                        <spring:theme code="text.account.subscriptions.paymentMethod.choose.row"/>
                                                    </label>
                                                    <%-- The label is composed by the adapter, because only it knows
                                                         whether it is describing a card, a mandate or an agreement. --%>
                                                    <select name="storedPaymentMethodId" id="subxCard-${row.index}"
                                                            class="form-control subx-select">
                                                        <c:if test="${not empty subscription.paymentMethodOptions}">
                                                            <optgroup label="<spring:theme code='text.account.subscriptions.paymentMethod.group.platform'/>">
                                                                <%-- Marked from what this subscription is billed to, not from the
                                                                     provider's default for the whole account: on a provider that pins a
                                                                     method per subscription those are routinely different cards. --%>
                                                                <c:forEach items="${subscription.paymentMethodOptions}" var="option">
                                                                    <option value="${fn:escapeXml(option.id)}"
                                                                            <c:if test="${option.id eq subscription.currentPaymentMethodId}">selected="selected"</c:if>>
                                                                        ${fn:escapeXml(option.displayLabel)}<c:if
                                                                                test="${option.id eq subscription.currentPaymentMethodId}">&nbsp;<spring:theme
                                                                                code="text.account.subscriptions.paymentMethod.default"/></c:if>
                                                                    </option>
                                                                </c:forEach>
                                                            </optgroup>
                                                        </c:if>
                                                        <%-- Already filtered by the facade: a card the platform could not
                                                             charge never reaches this list. --%>
                                                        <c:if test="${not empty subscription.adyenVaultOptions}">
                                                            <optgroup label="<spring:theme code='text.account.subscriptions.paymentMethod.group.vault'/>">
                                                                <c:forEach items="${subscription.adyenVaultOptions}" var="option">
                                                                    <option value="${fn:escapeXml(option.id)}">${fn:escapeXml(option.displayLabel)}</option>
                                                                </c:forEach>
                                                            </optgroup>
                                                        </c:if>
                                                    </select>
                                                    <p class="subx-note">
                                                        <spring:theme code="text.account.subscriptions.paymentMethod.note.SUBSCRIPTION"/>
                                                    </p>
                                                    <button type="submit" class="btn btn-default subx-btn">
                                                        <spring:theme code="text.account.subscriptions.paymentMethod.row.submit"/>
                                                    </button>
                                                </form:form>
                                            </div>
                                        </details>
                                    </ycommerce:testId>
                                </c:if>

                                <%-- The page offers the change somewhere, this subscription is in a state where a
                                     working card would matter, and it still cannot be changed here. Silence would
                                     leave it sitting under a control that does not apply to it. --%>
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
                                                <%-- Added by hand. This storefront configures its own CSRF
                                                     parameter name, so Spring's form tag does not supply it,
                                                     and POST is the only method the matcher checks. --%>
                                                <form:form action="${request.contextPath}/my-account/subscriptions/cancel"
                                                           method="post" id="subscriptionCancel-${row.index}">
                                                    <input type="hidden" name="${CSRFToken.parameterName}"
                                                           value="${CSRFToken.token}"/>
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
        </c:when>
        <c:when test="${empty ordersAwaitingSetup}">
            <div class="subx-empty">
                <spring:theme code="text.account.subscriptions.empty"/>
            </div>
        </c:when>
    </c:choose>
</div>
