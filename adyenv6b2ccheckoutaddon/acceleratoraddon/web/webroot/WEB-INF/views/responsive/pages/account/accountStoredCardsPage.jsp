<%@ page trimDirectiveWhitespaces="true"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="template" tagdir="/WEB-INF/tags/responsive/template"%>
<%@ taglib prefix="cms" uri="http://hybris.com/tld/cmstags"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<c:set var="noBorder" value=""/>
<c:if test="${not empty storedCards}">
    <c:set var="noBorder" value="no-border"/>
</c:if>

<div class="account-section-header ${noBorder}">
    <spring:theme code="text.account.storedCards" />
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
                                    <c:set var="mm" value="${fn:substring('0'.concat(fn:escapeXml(storedCard.expiryMonth)), fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))) - 2, fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))))}" />
                                    <c:set var="yyRaw" value="${fn:escapeXml(storedCard.expiryYear)}" />
                                    <c:set var="yy"
                                           value="${fn:length(yyRaw) == 2 ? '20'.concat(yyRaw) : yyRaw}" />
                                    ${mm}&nbsp;/&nbsp;${yy}
                                </li>
                            </ul>
                            <div class="account-cards-actions pull-left">
                                <ycommerce:testId code="storedCard_deletePayment_button" >
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
                            <div id="popup_confirm_payment_removal_${storedCard.id}" class="account-address-removal-popup">
                                <spring:theme code="text.account.storedCard.delete.following"/>
                                <div class="address">
                                    <strong>
                                            ${fn:escapeXml(storedCard.holderName)}
                                    </strong>
                                    <br><img src="https://live.adyen.com/hpp/img/pm/${storedCard.brand}.png"/>
                                    <br>****${fn:escapeXml(storedCard.lastFour)}
                                    <br>
                                    <c:set var="mm" value="${fn:substring('0'.concat(fn:escapeXml(storedCard.expiryMonth)), fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))) - 2, fn:length('0'.concat(fn:escapeXml(storedCard.expiryMonth))))}" />
                                    <c:set var="yyRaw" value="${fn:escapeXml(storedCard.expiryYear)}" />
                                    <c:set var="yy"
                                           value="${fn:length(yyRaw) == 2 ? '20'.concat(yyRaw) : yyRaw}" />
                                    ${mm}&nbsp;/&nbsp;${yy}
                                </div>
                                <c:url value="/my-account/stored-cards/remove" var="removePaymentActionUrl"/>
                                <form:form id="removeStoredCard${storedCard.id}" action="${removePaymentActionUrl}" method="post">
                                    <input type="hidden" name="paymentInfoId" value="${storedCard.id}"/>
                                    <br />
                                    <div class="modal-actions">
                                        <div class="row">
                                            <ycommerce:testId code="storedCardDelete_delete_button" >
                                                <div class="col-xs-12 col-sm-6 col-sm-push-6">
                                                    <button type="submit" class="btn btn-default btn-primary btn-block paymentsDeleteBtn">
                                                        <spring:theme code="text.account.storedCard.delete"/>
                                                    </button>
                                                </div>
                                            </ycommerce:testId>
                                            <div class="col-xs-12 col-sm-6 col-sm-pull-6">
                                                <a class="btn btn-default closeColorBox paymentsDeleteBtn btn-block" data-payment-id="${storedCard.id}">
                                                    <spring:theme code="text.button.cancel" />
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
            <spring:theme code="text.account.storedCards.empty" />
        </div>
    </c:otherwise>
</c:choose>