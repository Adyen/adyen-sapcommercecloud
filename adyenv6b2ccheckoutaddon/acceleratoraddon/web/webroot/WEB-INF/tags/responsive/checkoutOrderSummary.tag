<%@ attribute name="paymentMethod" required="true" type="java.lang.String" %>
<%@ attribute name="label" required="true" type="java.lang.String" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<spring:url value="/checkout/multi/adyen/summary/placeOrder" var="placeOrderUrl"/>
<spring:url value="/checkout/multi/termsAndConditions" var="getTermsAndConditionsUrl"/>

<c:set var="componentsWithPayButton"
       value="[amazonpay],[applepay],[paypal],[paywithgoogle],[googlepay],[pix],[iris],[bcmc_mobile],[upi],[paysafecard],[klarna],[klarna_account],[klarna_paynow],[klarna_b2b],[ideal],[trustly],[swish],[twint],[paypo],[paybybank],[paybybank_AIS_DD],[vipps],[mobilepay],[ebanking_FI],[onlineBanking_PL],[multibanco]"/>
<c:set var="componentPaymentMethod" value="[${selectedPaymentMethod}]" />

<%-- Components --%>
<c:if test="${fn:contains(componentsWithPayButton, componentPaymentMethod)}">
    <c:if test="${componentPaymentMethod ne '[upi]'}">
    <div class="checkbox">
        <label>
            <input type="checkbox" id="terms-conditions-check-${label}" class="adyen-terms-conditions-check" />
            <spring:theme var="readTermsAndConditions" code="checkout.summary.placeOrder.readTermsAndConditions" arguments="${fn:escapeXml(getTermsAndConditionsUrl)}" htmlEscape="false"/>
                ${ycommerce:sanitizeHTML(readTermsAndConditions)}
        </label>

        <div class="adyen-terms-conditions-check-error hidden">
            <spring:theme code="checkout.error.terms.not.accepted" />
        </div>
    </c:if>
    </div>
    <c:choose>
        <c:when test="${componentPaymentMethod eq '[pix]' || componentPaymentMethod eq '[iris]' || componentPaymentMethod eq '[bcmc_mobile]'}">
            <%-- Render QR code --%>
            <button id="generateqr-${label}" type="submit" class="btn btn-primary btn-block">
                <spring:theme code="checkout.summary.component.generateqr" text="Generate QR Code" />
            </button>
            <div id="qrcode-container-${label}"></div>
        </c:when>
        <c:otherwise>
            <%-- Render payment button --%>
            <div id="adyen-component-button-container-${label}"></div>
        </c:otherwise>
    </c:choose>
</c:if>

<c:if test="${componentPaymentMethod eq '[mbway]'}">
    <div class="chckt-pm__header js-chckt-pm__header">
        <spring:theme code="checkout.summary.component.mbway.payment"/>
    </div>
    <div id="adyen-component-container-${label}"></div>
</c:if>

<c:if test="${componentPaymentMethod eq '[blik]'}">
    <div class="chckt-pm__header js-chckt-pm__header">
        <spring:theme code="checkout.summary.component.blik.payment"/>
    </div>
    <div id="adyen-component-container-${label}"></div>
</c:if>

<c:if test="${componentPaymentMethod eq '[giftcard]'}">
    <div class="chckt-pm__header js-chckt-pm__header">
        <spring:theme code="checkout.summary.component.giftcard.payment"/>
    </div>
    <div id="adyen-component-container-${label}"></div>
</c:if>

<c:if test="${componentPaymentMethod eq '[googlepay]'}">
    <div class="chckt-pm__header js-chckt-pm__header">
        <spring:theme code="checkout.summary.component.googlepay.payment"/>
    </div>
    <div id="adyen-component-container-${label}"></div>
</c:if>

<%-- For components that do not have it's own button --%>
<c:if test="${not fn:contains(componentsWithPayButton, componentPaymentMethod)}">
    <form:form action="${placeOrderUrl}" id="placeOrderForm-${label}" modelAttribute="placeOrderForm">
        <div class="checkbox">
            <label> <form:checkbox id="terms-conditions-check-${label}" path="termsCheck" />
                <spring:theme var="readTermsAndConditions" code="checkout.summary.placeOrder.readTermsAndConditions" arguments="${fn:escapeXml(getTermsAndConditionsUrl)}" htmlEscape="false"/>
                    ${ycommerce:sanitizeHTML(readTermsAndConditions)}
            </label>
        </div>
    </form:form>
    <div id="adyen-component-container-${label}"></div>
    <button id="placeOrder-${label}" type="submit" class="btn btn-primary btn-place-order btn-block">
        <spring:theme code="checkout.summary.placeOrder" text="Place Order" />
    </button>
</c:if>
