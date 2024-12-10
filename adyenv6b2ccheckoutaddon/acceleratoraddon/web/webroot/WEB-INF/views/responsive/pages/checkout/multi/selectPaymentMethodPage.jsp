<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="template" tagdir="/WEB-INF/tags/responsive/template" %>
<%@ taglib prefix="cms" uri="http://hybris.com/tld/cmstags" %>
<%@ taglib prefix="multiCheckout" tagdir="/WEB-INF/tags/responsive/checkout/multi" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="formElement" tagdir="/WEB-INF/tags/responsive/formElement" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="address" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>

<c:url value="${currentStepUrl}" var="choosePaymentMethodUrl"/>
<c:url value="/checkout/multi/adyen/select-payment-method" var="selectPaymentMethod"/>
<template:page pageTitle="${pageTitle}" hideHeaderLinks="true">
    <jsp:attribute name="pageScripts">
        <adyen:adyenLibrary
                dfUrl="${dfUrl}"
                showDefaultCss="${true}"
        />
        <script type="text/javascript">

            const { AdyenCheckout, Dropin, Card, PayPal, GooglePay,
                    ApplePay, CashAppPay, Sepa,Redirect,OnlineBankingIN,
                    OnlineBankingPL, Ideal, EPS, Pix, WalletIN, AfterPay, Bcmc,
                    Pos, PayBright, Boleto, SepaDirectDebit, RatePay, Paytm,Giftcard,Blik
            } = AdyenWeb;

            <c:if test="${not empty allowedCards}">
            //Set the allowed cards
            const allowedCards = [];
            <c:forEach items="${allowedCards}" var="allowedCard">
            allowedCards.push("${allowedCard}");
            </c:forEach>

            const initConfig = {
                shopperLocale: "${shopperLocale}",
                environment: "${environmentMode}",
                clientKey: "${clientKey}",
                session: {
                    id: "${sessionData.id}",
                    sessionData: "${sessionData.sessionData}",
                }
            };
            const paymentMethodConfigs = {};

            /**
             * Generate array of available payment methods to initialize
             */
            paymentMethodConfigs['createCard'] = {
                allowedCards,
                showRememberDetails: ${showRememberTheseDetails},
                cardHolderNameRequired: ${cardHolderNameRequired}
            }

            const adyenCheckout = new AdyenCheckoutHelper();

            <c:if test="${sepadirectdebit}">
            paymentMethodConfigs['createSepaDirectDebit'] = null;
            </c:if>

            <c:if test="${not empty issuerLists['ideal']}">
            paymentMethodConfigs['createIdeal'] = ${issuerLists['ideal']};
            </c:if>

            <c:if test="${not empty issuerLists['onlinebanking_IN']}">
            paymentMethodConfigs['createOnlinebankingIN'] = null;
            </c:if>

            <c:if test="${not empty issuerLists['onlineBanking_PL']}">
            paymentMethodConfigs['createOnlineBankingPL'] = null;
            </c:if>

            <c:if test="${not empty issuerLists['eps']}">
            paymentMethodConfigs['createEps'] = ${issuerLists['eps']};
            </c:if>

            <c:if test="${not empty issuerLists['pix']}">
            paymentMethodConfigs['createPix'] = {
                label: null,
                issuers: ${issuerLists['pix']}
            };
            </c:if>

            <c:forEach var="paymentMethod" items="${paymentMethods}">

            <c:if test="${paymentMethod.type eq 'wallet_IN'}">
            paymentMethodConfigs['createWalletIN'] = null;
            </c:if>
            //TO-DO Refactor the code to get a returnUrl service and add another endpoint to manage the result
            //Adding here paytm payment method, there is already an createPaytm function defined on the adyen.checkout.js
            <c:if test="${paymentMethod.type eq 'afterpay_default'}">
            paymentMethodConfigs['createAfterPay'] = "${countryCode}";
            </c:if>

            <c:if test="${paymentMethod.type eq 'bcmc'}">
            paymentMethodConfigs['createBcmc'] = "${countryCode}";
            </c:if>

            </c:forEach>

            let storedCardJS;

            <c:forEach items="${storedCards}" var="storedCard">

            storedCardJS = {
                storedPaymentMethodId: "${storedCard.id}",
                name: "${storedCard.name}",
                type: "${storedCard.type}",
                brand: "${storedCard.brand}",
                lastFour: "${storedCard.lastFour}",
                expiryMonth: "${storedCard.expiryMonth}",
                expiryYear: "${storedCard.expiryYear}",
                holderName: "${storedCard.holderName}",
                supportedShopperInteractions: "${storedCard.supportedShopperInteractions}",
                shopperEmail: "${storedCard.shopperEmail}"
            };

            if (paymentMethodConfigs['createOneClickCard']) {
                paymentMethodConfigs['createOneClickCard'].push(storedCardJS);
            } else {
                paymentMethodConfigs['createOneClickCard'] = [storedCardJS];
            }
            </c:forEach>

            adyenCheckout.initiateCheckout(initConfig, paymentMethodConfigs);
            </c:if>

            //Handle form submission
            $(".submit_silentOrderPostForm").click(function () {
                if (!adyenCheckout.validateForm()) {
                    return false;
                }
                adyenCheckout.setCustomPaymentMethodValues();
                adyenCheckout.addRiskData();

                $("#adyen-encrypted-form").submit();
            });

            <c:if test="${not empty selectedPaymentMethod}">
            adyenCheckout.togglePaymentMethod("${selectedPaymentMethod}");
            $('input[type=radio][name=paymentMethod][value="${selectedPaymentMethod}"]').prop("checked", true);
            </c:if>

            // Toggle payment method specific areas (credit card form and issuers list)
            $('input[type=radio][name=paymentMethod]').change(function () {
                adyenCheckout.togglePaymentMethod(this.value);
            });

            adyenCheckout.createDobDatePicker("p_method_adyen_hpp_dob");
            adyenCheckout.createDfValue();
        </script>
    </jsp:attribute>

    <jsp:body>
        <div class="row">
            <div class="col-sm-6">
                <div class="checkout-headline">
                    <span class="glyphicon glyphicon-lock"></span>
                    <spring:theme code="checkout.multi.secure.checkout"/>
                </div>
                <multiCheckout:checkoutSteps checkoutSteps="${checkoutSteps}" progressBarId="${progressBarId}">
                    <jsp:body>
                        <form:form method="post" modelAttribute="adyenPaymentForm"
                                   class="create_update_payment_form"
                                   id="adyen-encrypted-form" action="${selectPaymentMethod}"
                        >

                            <form:hidden path="cseToken"/>
                            <form:hidden path="selectedReference"/>
                            <form:hidden path="issuerId"/>
                            <form:hidden path="upiVirtualAddress"/>
                            <form:hidden path="dob"/>
                            <form:hidden path="socialSecurityNumber"/>
                            <form:hidden path="firstName"/>
                            <form:hidden path="lastName"/>
                            <form:hidden path="dfValue"/>
                            <form:hidden path="cardHolder"/>
                            <form:hidden path="cardBrand"/>
                            <form:hidden path="cardType"/>

                            <form:hidden path="encryptedCardNumber"/>
                            <form:hidden path="encryptedExpiryMonth"/>
                            <form:hidden path="encryptedExpiryYear"/>
                            <form:hidden path="encryptedSecurityCode"/>

                            <form:hidden path="browserInfo"/>
                            <form:hidden path="terminalId"/>
                            <form:hidden path="rememberTheseDetails" value="false"/>
                            <form:hidden path="sepaOwnerName"/>
                            <form:hidden path="sepaIbanNumber"/>
                            <form:hidden path="giftCardBrand"/>
                            <form:hidden path="riskData"/>


                            <%-- Billing Information --%>
                            <div class="headline">
                                <spring:theme code="payment.method.billing.information"/>
                            </div>

                            <c:if test="${cartData.deliveryItemsQuantity > 0}">
                                <div id="useAdyenDeliveryAddressData"
                                     data-titlecode="${fn:escapeXml(deliveryAddress.titleCode)}"
                                     data-firstname="${fn:escapeXml(deliveryAddress.firstName)}"
                                     data-lastname="${fn:escapeXml(deliveryAddress.lastName)}"
                                     data-line1="${fn:escapeXml(deliveryAddress.line1)}"
                                     data-line2="${fn:escapeXml(deliveryAddress.line2)}"
                                     data-town="${fn:escapeXml(deliveryAddress.town)}"
                                     data-postalcode="${fn:escapeXml(deliveryAddress.postalCode)}"
                                     data-countryisocode="${fn:escapeXml(deliveryAddress.country.isocode)}"
                                     data-regionisocode="${fn:escapeXml(deliveryAddress.region.isocodeShort)}"
                                     data-address-id="${fn:escapeXml(deliveryAddress.id)}"
                                ></div>

                                <formElement:formCheckbox
                                        path="useAdyenDeliveryAddress"
                                        idKey="useAdyenDeliveryAddress"
                                        labelKey="checkout.multi.sop.useMyDeliveryAddress"
                                        tabindex="11"/>
                            </c:if>

                            <address:billAddressFormSelector supportedCountries="${countries}" regions="${regions}"
                                                             tabindex="12"/>

                            <%-- Billing Information end --%>

                            <div class="headline">
                                <spring:theme code="payment.method.payment.information"/>
                            </div>

                            <div class="chckt-pm-list js-chckt-pm-list">
                                <c:forEach items="${storedCards}" var="storedCard">
                                    <adyen:storedCardMethod
                                            variant="${storedCard.brand}"
                                            cardReference="${storedCard.id}"
                                            cardNumber="${storedCard.lastFour}"
                                    />
                                </c:forEach>

                                <c:if test="${not empty allowedCards}">
                                    <adyen:securedFieldsMethod
                                            creditCardLabel="${creditCardLabel}"
                                            showComboCard="${showComboCard}"/>
                                </c:if>

                                    <%--to-do populate issuers and rest of items via checkout components--%>
                                <c:forEach items="${paymentMethods}" var="paymentMethod">
                                    <adyen:alternativeMethod
                                            brandCode="${paymentMethod.type}"
                                            brand="${paymentMethod.brand}"
                                            name="${paymentMethod.name}"
                                            showDob="${paymentMethod.type=='ratepay'}"
                                            showFirstName="${paymentMethod.type=='pix'}"
                                            showLastName="${paymentMethod.type=='pix'}"
                                            showSocialSecurityNumber="${showSocialSecurityNumber || paymentMethod.type=='pix'}"
                                            countryCode="${countryCode}"
                                            showTelephoneNumber="${paymentMethod.type=='paybright'}"
                                    />
                                </c:forEach>

                                <c:if test="${sepadirectdebit}">
                                    <adyen:alternativeMethod
                                            brandCode="sepadirectdebit"
                                            name="SEPA Direct Debit"
                                    />
                                </c:if>

                                <c:if test="${not empty issuerLists['ideal']}">
                                    <adyen:alternativeMethod
                                            brandCode="ideal"
                                            name="iDEAL"
                                    />
                                </c:if>

                                <c:if test="${not empty issuerLists['eps']}">
                                    <adyen:alternativeMethod
                                            brandCode="eps"
                                            name="EPS"
                                    />
                                </c:if>

                                <c:if test="${showBoleto}">
                                    <adyen:alternativeMethod
                                            brandCode="boleto"
                                            name="Boleto"
                                            showSocialSecurityNumber="true"
                                            showFirstName="true"
                                            showLastName="true"
                                            countryCode="BR"
                                    />
                                </c:if>
                                <c:if test="${not empty connectedTerminalList}">
                                    <adyen:alternativeMethod
                                            brandCode="pos"
                                            name="POS"
                                            showTerminalList="true"
                                    />
                                </c:if>
                            </div>
                        </form:form>

                        <button type="button"
                                class="btn btn-primary btn-block submit_silentOrderPostForm checkout-next">
                            <spring:theme code="checkout.multi.paymentMethod.continue"/>
                        </button>

                    </jsp:body>
                </multiCheckout:checkoutSteps>
            </div>

            <div class="col-sm-6 hidden-xs">
                <multiCheckout:checkoutOrderDetails cartData="${cartData}" showDeliveryAddress="true"
                                                    showPaymentInfo="false" showTaxEstimate="false" showTax="true"/>
            </div>

            <div class="col-sm-12 col-lg-12">
                <cms:pageSlot position="SideContent" var="feature" element="div" class="checkout-help">
                    <cms:component component="${feature}"/>
                </cms:pageSlot>
            </div>
        </div>
    </jsp:body>
</template:page>
