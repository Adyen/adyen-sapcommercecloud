<%--
    The "My subscriptions" page body.

    A fragment, not a page: it is rendered inside a ContentSlot by a JspIncludeComponent, so there is no
    <template:page> here. Wrapping it in one produces a page inside a page, or nothing at all.

    One section per billing platform whose connector is loaded and on which the shopper has something to see,
    one under the other. Each section is rendered by its own tag from its own data and posts its own platform
    with every form, so subscriptions and controls of two platforms never mix.
--%>
<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="subscriptions" tagdir="/WEB-INF/tags/addons/adyensubscriptionb2caddon/responsive/subscriptions" %>

<div class="subx">

    <div class="account-section-header">
        <spring:theme code="text.account.subscriptions"/>
    </div>

    <c:choose>
        <c:when test="${not empty subscriptionSections}">
            <c:forEach items="${subscriptionSections}" var="subscriptionSection">
                <subscriptions:platformSection section="${subscriptionSection}"/>
            </c:forEach>
        </c:when>
        <c:otherwise>
            <div class="subx-empty">
                <spring:theme code="text.account.subscriptions.empty"/>
            </div>
        </c:otherwise>
    </c:choose>
</div>
