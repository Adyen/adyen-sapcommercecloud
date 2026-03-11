<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="template" tagdir="/WEB-INF/tags/responsive/template" %>
<%@ taglib prefix="cms" uri="http://hybris.com/tld/cmstags" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>

<c:set var="noBorder" value=""/>
<c:if test="${not empty storedCards}">
    <c:set var="noBorder" value="no-border"/>
</c:if>

<div class="account-section-header ${noBorder}">
    <spring:theme code="text.account.storedCards"/>
</div>

<c:choose>
    <c:when test="${not empty storedCards}">
        <div class="account-storedCards account-list">
            <div class="account-cards card-select">
                <div class="row">
                    <c:forEach items="${storedCards}" var="storedCard">
                        <div class="col-xs-12 col-sm-6 col-md-4 card">
                            <ul class="pull-left">
                                <li>${fn:escapeXml(storedCard.holderName)}</li>
                                <li><img src="https://live.adyen.com/hpp/img/pm/${storedCard.brand}.png"/></li>
                                <li>****${fn:escapeXml(storedCard.lastFour)}</li>
                                <li>
                                    <c:set var="formattedExpiryMonth"
                                           value="${fn:substring('0'.concat(fn:escapeXml(storedCard.expiryMonth)), fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))) - 2, fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))))}"/>
                                    <c:set var="expiryYear" value="${fn:escapeXml(storedCard.expiryYear)}"/>
                                    <c:set var="formattedExpiryYear"
                                           value="${fn:length(expiryYear) == 2 ? '20'.concat(expiryYear) : expiryYear}"/>
                                        ${formattedExpiryMonth}&nbsp;/&nbsp;${formattedExpiryYear}
                                </li>
                            </ul>
                            <div class="account-cards-actions pull-left">
                                <ycommerce:testId code="storedCard_deletePayment_button">
                                    <a class="action-links removePaymentDetailsButton"
                                       href="#"
                                       data-payment-id="${storedCard.id}"
                                       data-popup-title="<spring:theme code="text.account.storedCard.delete.popup.title"/>">
                                        <span class="glyphicon glyphicon-remove" aria-hidden="true"></span>
                                    </a>
                                </ycommerce:testId>
                            </div>
                        </div>

                        <div class="display-none">
                            <div id="popup_confirm_payment_removal_${storedCard.id}"
                                 class="account-address-removal-popup">
                                <spring:theme code="text.account.storedCard.delete.following"/>
                                <div class="address">
                                    <strong>
                                            ${fn:escapeXml(storedCard.holderName)}
                                    </strong>
                                    <br><img src="https://live.adyen.com/hpp/img/pm/${storedCard.brand}.png"/>
                                    <br>****${fn:escapeXml(storedCard.lastFour)}
                                    <br>
                                    <c:set var="formattedExpiryMonth"
                                           value="${fn:substring('0'.concat(fn:escapeXml(storedCard.expiryMonth)), fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))) - 2, fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))))}"/>
                                    <c:set var="expiryYear" value="${fn:escapeXml(storedCard.expiryYear)}"/>
                                    <c:set var="formattedExpiryYear"
                                           value="${fn:length(expiryYear) == 2 ? '20'.concat(expiryYear) : expiryYear}"/>
                                        ${formattedExpiryMonth}&nbsp;/&nbsp;${formattedExpiryYear}
                                </div>
                                <c:url value="/my-account/stored-cards/remove" var="removePaymentActionUrl"/>
                                <form:form id="removeStoredCard${storedCard.id}" action="${removePaymentActionUrl}"
                                           method="post">
                                    <input type="hidden" name="paymentInfoId" value="${storedCard.id}"/>
                                    <br/>
                                    <div class="modal-actions">
                                        <div class="row">
                                            <ycommerce:testId code="storedCardDelete_delete_button">
                                                <div class="col-xs-12 col-sm-6 col-sm-push-6">
                                                    <button type="submit"
                                                            class="btn btn-default btn-primary btn-block paymentsDeleteBtn">
                                                        <spring:theme code="text.account.storedCard.delete"/>
                                                    </button>
                                                </div>
                                            </ycommerce:testId>
                                            <div class="col-xs-12 col-sm-6 col-sm-pull-6">
                                                <a class="btn btn-default closeColorBox paymentsDeleteBtn btn-block"
                                                   data-payment-id="${storedCard.id}">
                                                    <spring:theme code="text.button.cancel"/>
                                                </a>
                                            </div>
                                        </div>
                                    </div>
                                </form:form>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </div>
        </div>
    </c:when>
    <c:otherwise>
        <div class="account-section-content content-empty">
            <spring:theme code="text.account.storedCards.empty"/>
        </div>
    </c:otherwise>
</c:choose>

<div class="account-section-header" style="margin-top: 30px;">
    Add new card
</div>

<div class="account-section-content">
    <div id="adyen-myaccount-config"
         data-client-key="${adyenClientKey}"
         data-environment="${adyenEnvironment}"
         data-locale="${adyenLocale}"
         data-country-code="${adyenCountryCode}">
    </div>

    <div id="adyen-myaccount-card" style="max-width: 500px;"></div>

    <div style="margin-top: 16px;">
        <button id="adyen-tokenize-card-btn"
                type="button"
                class="btn btn-primary"
                disabled="disabled">
            Save card
        </button>
    </div>

    <div id="adyen-tokenize-card-msg" style="margin-top: 12px;"></div>
</div>
<div id="adyen-popup-overlay"
     style="display:none; position:fixed; inset:0; background:rgba(0,0,0,0.4); z-index:9998;">
</div>

<div id="adyen-popup"
     style="
        display:none;
        position:fixed;
        top:50%;
        left:50%;
        transform:translate(-50%, -50%);
        background:#fff;
        padding:24px;
        min-width:320px;
        border-radius:8px;
        box-shadow:0 4px 20px rgba(0,0,0,0.2);
        z-index:9999;
        text-align:center;
     ">

    <div id="adyen-popup-title" style="font-weight:bold; margin-bottom:12px;">
        Message
    </div>

    <div id="adyen-popup-message" style="margin-bottom:16px;"></div>

    <button id="adyen-popup-close"
            type="button"
            class="btn btn-primary"
            style="margin-top:10px;">
        OK
    </button>

</div>
<adyen:adyenLibrary showDefaultCss="false"/>
<script src="${contextPath}/_ui/addons/adyenv6b2ccheckoutaddon/responsive/common/js/adyenMyAccountStoredCards.js"></script>