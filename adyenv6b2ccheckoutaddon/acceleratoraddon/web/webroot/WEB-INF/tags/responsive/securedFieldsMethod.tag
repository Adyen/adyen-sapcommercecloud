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
<%@ attribute name="creditCardLabel" required="true" type="java.lang.String" %>
<%@ attribute name="showComboCard" required="false" type="java.lang.Boolean" %>

<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>

<div class="chckt-pm chckt-pm-card js-chckt-pm js-chckt-pm__pm-holder" data-additional-required="" data-pm="card">
    <input type="hidden" name="txvariant" value="card">
    <div class="chckt-pm__header js-chckt-pm__header">
        <adyen:methodSelector name="card"/>
        <span class="chckt-pm__name js-chckt-pm__name"><spring:theme code="payment.method.label.information"/></span>
        <span class="chckt-pm__image">
            <span id="cardLogos"></span>
            <span class="chckt-pm__image-border"></span>
        </span>
    </div>

    <div class="chckt-pm__details js-chckt-pm__details payment_method_details" id="dd_method_card">
        <div class="chckt-form chckt-form--max-width">
            <c:if test="${showComboCard}">
                <div class="adyen-checkout__dropdown adyen-checkout__field adyen-checkout__input--large">
                    <label class="adyen-checkout__label__text">
                        <spring:theme code="payment.method.cards.combo"/>
                    </label>
                    <select class="adyen-checkout__dropdown__button chckt-select-box js-chckt-terminal-select-box" id="adyen_combo_card_type"
                            name="${adyen_combo_card_type}">
                        <option value="credit">
                            <spring:theme code="payment.method.cards.credit"/>
                        </option>
                        <option value="debit">
                            <spring:theme code="payment.method.cards.debit"/>
                        </option>
                    </select>
                    <span class="clearfix"></span>
                </div>
            </c:if>
            <div id="card-div"></div>
        </div>
    </div>
</div>
