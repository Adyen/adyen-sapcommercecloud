<%--
    The <option> list of a payment-method picker, shared by the account-wide control and the in-row one.

    Two sources, grouped only when both are present: what the billing provider already holds, and cards from
    the shopper's Adyen vault the provider would accept. The facade has already removed a vaulted card the
    provider holds under its own identifier, so each card appears once. Labels are composed server-side,
    because only the provider's adapter knows whether it is describing a card, a mandate or an agreement.
--%>
<%@ tag body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ attribute name="platformOptions" required="true" type="java.util.List" %>
<%@ attribute name="vaultOptions" required="true" type="java.util.List" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<c:set var="grouped" value="${not empty platformOptions and not empty vaultOptions}"/>

<c:if test="${not empty platformOptions}">
    <c:if test="${grouped}"><optgroup label="<spring:theme code='text.account.subscriptions.paymentMethod.group.platform'/>"></c:if>
    <%-- Marked from what is billing now, not from the provider's default for the whole account: on a provider
         that pins a method per subscription those are routinely different methods. --%>
    <c:forEach items="${platformOptions}" var="option">
        <option value="${fn:escapeXml(option.id)}"<c:if test="${option.current}"> selected="selected"</c:if>>
            ${fn:escapeXml(option.displayLabel)}<c:if test="${option.current}">&nbsp;<spring:theme
                code="text.account.subscriptions.paymentMethod.default"/></c:if>
        </option>
    </c:forEach>
    <c:if test="${grouped}"></optgroup></c:if>
</c:if>

<c:if test="${not empty vaultOptions}">
    <c:if test="${grouped}"><optgroup label="<spring:theme code='text.account.subscriptions.paymentMethod.group.vault'/>"></c:if>
    <c:forEach items="${vaultOptions}" var="option">
        <option value="${fn:escapeXml(option.id)}">${fn:escapeXml(option.displayLabel)}</option>
    </c:forEach>
    <c:if test="${grouped}"></optgroup></c:if>
</c:if>
