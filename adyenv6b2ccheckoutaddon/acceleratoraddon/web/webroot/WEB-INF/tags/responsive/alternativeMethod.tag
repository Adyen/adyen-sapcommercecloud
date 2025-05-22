<%--
  ~                        ######
  ~                        ######
  ~  ############    ####( ######  #####. ######  ############   ############
  ~  #############  #####( ######  #####. ######  #############  #############
  ~         ######  #####( ######  #####. ######  #####  ######  #####  ######
  ~  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
  ~  ###### ######  #####( ######  #####. ######  #####          #####  ######
  ~  #############  #############  #############  #############  #####  ######
  ~   ############   ############  #############   ############  #####  ######
  ~                                       ######
  ~                                #############
  ~                                ############
  ~
  ~  Adyen Hybris Extension
  ~
  ~  Copyright (c) 2017 Adyen B.V.
  ~  This file is open source and available under the MIT license.
  ~  See the LICENSE file for more info.
  --%>
<%@ attribute name="brandCode" required="true" type="java.lang.String" %>
<%@ attribute name="brand" required="false" type="java.lang.String" %>
<%@ attribute name="name" required="true" type="java.lang.String" %>
<%@ attribute name="issuers" required="false" type="java.util.List" %>
<%@ attribute name="showDob" required="false" type="java.lang.Boolean" %>
<%@ attribute name="showSocialSecurityNumber" required="false" type="java.lang.Boolean" %>
<%@ attribute name="showFirstName" required="false" type="java.lang.Boolean" %>
<%@ attribute name="showLastName" required="false" type="java.lang.Boolean" %>
<%@ attribute name="countryCode" required="false" type="java.lang.String" %>
<%@ attribute name="showTelephoneNumber" required="false" type="java.lang.Boolean" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>

<div class="chckt-pm chckt-pm-${brandCode} js-chckt-pm" data-pm="${brandCode}">
    <div class="chckt-pm__header js-chckt-pm__header">
        <adyen:methodSelector name="${brandCode}" brand="${brand}"/>
        <span class="chckt-pm__name js-chckt-pm__name">${name}</span>
        <span class="chckt-pm__image">
            <img width="40" src="https://checkoutshopper-live.adyen.com/checkoutshopper/images/logos/${brandCode}.svg" alt="">
            <span class="chckt-pm__image-border"></span>
        </span>
    </div>
    <div class="chckt-pm__details js-chckt-pm__details" id="adyen_hpp_${brandCode}_container">
        <div class="chckt-form chckt-form--max-width">
            <c:if test="${not empty issuers}">
                <label class="chckt-form-label chckt-form-label--full-width">
                    <select class="chckt-select-box js-chckt-issuer-select-box" id="p_method_adyen_hpp_${brandCode}_issuer" name="${brandCode}Issuer">
                        <option value="">
                            <spring:theme code="payment.method.issuer.selector"/>
                        </option>
                        <c:forEach items="${issuers}" var="issuer">
                            <option value="${issuer.id}">${issuer.name}</option>
                        </c:forEach>
                    </select>
                </label>
            </c:if>


            <c:if test="${showDob}">
                <label for="p_method_adyen_hpp_${brandCode}_dob">
                    <span>
                        <spring:theme code="payment.method.date.of.birth"/>
                    </span>
                </label>
                <input id="p_method_adyen_hpp_${brandCode}_dob"
                       class="p_method_adyen_hpp_dob"
                       type="text"
                       title="date of birth">
            </c:if>

            <c:if test="${showSocialSecurityNumber}">
                <c:choose>
                    <c:when test="${countryCode=='BR'}">
                        <label for="p_method_adyen_hpp_${brandCode}_ssn">
                            <span>
                                <spring:theme code="payment.method.social.security.number"/>
                            </span>
                            <input id="p_method_adyen_hpp_${brandCode}_ssn" class="p_method_adyen_hpp_ssn" type="text">
                        </label>
                    </c:when>
                    <c:otherwise>
                        <label for="p_method_adyen_hpp_${brandCode}_ssn">
                            <span>
                                <spring:theme code="payment.method.personal.number"/>
                            </span>
                            <input id="p_method_adyen_hpp_${brandCode}_ssn" class="p_method_adyen_hpp_ssn" type="text" size="4" title="personal number">
                        </label>
                    </c:otherwise>
                </c:choose>
            </c:if>

            <c:if test="${showFirstName}">
                <label for="p_method_adyen_hpp_${brandCode}_first_name">
                    <span>
                        <spring:theme code="payment.method.first.name"/>
                    </span>
                    <input id="p_method_adyen_hpp_${brandCode}_first_name"
                           type="text"
                           name="${brandCode}_firstName"
                           value="${cartData.deliveryAddress.firstName}">
                </label>
            </c:if>

            <c:if test="${showLastName}">
                <label for="p_method_adyen_hpp_${brandCode}_last_name">
                    <span>
                        <spring:theme code="payment.method.last.name"/>
                    </span>
                    <input id="p_method_adyen_hpp_${brandCode}_last_name"
                           type="text"
                           name="${brandCode}_lastName"
                           value="${cartData.deliveryAddress.lastName}">
                </label>
            </c:if>

            <c:if test="${showTelephoneNumber}">
                <label for="p_method_adyen_hpp_${brandCode}_telephonenumber">
                    <span>
                        <spring:theme code="payment.method.telephonenumber"/>
                    </span>
                    <input id="p_method_adyen_hpp_${brandCode}_telephonenumber"
                           type="text"
                           name="telephoneNumber"
                           value="${cartData.deliveryAddress.phone}">
                </label>
            </c:if>

            <c:if test="${brandCode eq 'afterpay_default'}">
                <div id="afterpay-container"></div>
            </c:if>

            <c:if test="${brandCode eq 'bcmc'}">
                <div id="bcmc-container"></div>
            </c:if>
        </div>
    </div>
</div>
