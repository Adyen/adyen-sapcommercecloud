<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="formElement" tagdir="/WEB-INF/tags/responsive/formElement" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="order" tagdir="/WEB-INF/tags/responsive/order" %>
<%@ taglib prefix="template" tagdir="/WEB-INF/tags/responsive/template"%>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>

<spring:htmlEscape defaultHtmlEscape="true" />
<spring:url value="/login/register/termsandconditions" var="getTermsAndConditionsUrl" htmlEscape="false"/>

<div class="checkout-success">
    <div class="checkout-success__body">
        <div class="checkout-success__body__headline">
            <spring:theme code="checkout.orderConfirmation.thankYouForOrder" />
        </div>
        <p><spring:theme code="text.account.order.orderNumberLabel"/><strong> ${fn:escapeXml(orderData.code)}</strong></p>
        <p><spring:theme code="checkout.orderConfirmation.copySentToShort"/><strong> ${fn:escapeXml(email)}</strong></p>
    </div>

    <order:giftCoupons giftCoupons="${giftCoupons}"/>

    <%-- The token remains in the server session. This node is mounted only
         when the Giving context endpoint confirms that a donation is eligible. --%>
    <div id="adyen-giving" class="adyen-giving" aria-live="polite"></div>

    <c:if test="${not empty guestRegisterForm}">
        <div class="checkout__new-account">
            <div class="checkout__new-account__headline"><spring:theme code="guest.register"/></div>
            <p><spring:theme code="order.confirmation.guest.register.description"/></p>

            <form:form method="post" modelAttribute="guestRegisterForm" class="checkout__new-account__form clearfix">
                <div class="col-sm-8 col-sm-push-2 col-md-6 col-md-push-3">
                    <form:hidden path="orderCode"/>
                    <form:hidden path="uid"/>

                    <div class="form-group">
                        <label for="email" class="control-label "><spring:theme code="register.email"/></label>
                        <input type="text" value="${fn:escapeXml(guestRegisterForm.uid)}" class="form-control" name="email" id="email" readonly>
                    </div>

                    <formElement:formPasswordBox idKey="password" labelKey="guest.pwd" path="pwd" inputCSS="password strength form-control" mandatory="true"/>
                    <formElement:formPasswordBox idKey="guest.checkPwd" labelKey="guest.checkPwd" path="checkPwd" inputCSS="password form-control" mandatory="true"/>
                    <c:if test="${not empty consentTemplateData}">
                        <form:hidden path="consentForm.consentTemplateId" value="${consentTemplateData.id}" />
                        <form:hidden path="consentForm.consentTemplateVersion" value="${consentTemplateData.version}" />
                        <div class="checkbox">
                            <label class="control-label uncased">
                                <form:checkbox path="consentForm.consentGiven" />
                                <c:out value="${consentTemplateData.description}" />
                            </label>
                        </div>
                        <div class="help-block">
                            <spring:theme code="registration.consent.link" />
                        </div>
                    </c:if>
                    <template:errorSpanField path="termsCheck">
                        <div class="checkbox">
                            <label class="control-label uncased">
                                <form:checkbox id="registerChkTermsConditions" path="termsCheck" disabled="true"/>
                                <spring:theme var="termsAndConditionsHtml" code="register.termsConditions" arguments="${fn:escapeXml(getTermsAndConditionsUrl)}" htmlEscape="false" />
                                ${ycommerce:sanitizeHTML(termsAndConditionsHtml)}
                            </label>
                        </div>
                    </template:errorSpanField>
                    <div class="accountActions-bottom">
                        <ycommerce:testId code="guest_Register_button">
                            <button type="submit" class="btn btn-block btn-primary" disabled="disabled">
                                <spring:theme code="guest.register"/>
                            </button>
                        </ycommerce:testId>
                    </div>
                </div>
            </form:form>
        </div>
    </c:if>
</div>

<%-- adyenLibrary reads checkoutShopperHost; the JSP checkout controller exposes
     the same value as adyenCheckoutShopperHost. --%>
<c:set var="checkoutShopperHost" value="${adyenCheckoutShopperHost}"/>
<adyen:adyenLibrary showDefaultCss="true"/>
<%-- The checkout tag ships 6.40.1, whose global bundle does not expose
     Donation.  Keep the checkout-wide version untouched and load the same
     6.41.0 full bundle that is resolved by the React checkout. --%>
<script src="https://${checkoutShopperHost}/checkoutshopper/sdk/6.41.0/adyen.js"
        crossorigin="anonymous"></script>
<script type="text/javascript">
    (function () {
        var contextPath = '${fn:escapeXml(encodedContextPath)}';
        var csrfToken = '${ycommerce:encodeJavaScript(CSRFToken.token)}';

        function showGivingError(error) {
            var node = document.getElementById('adyen-giving');
            if (node) {
                node.innerHTML = '';
                var message = document.createElement('p');
                message.className = 'alert alert-danger';
                message.textContent = 'Giving could not be displayed'
                    + (error && error.message ? ': ' + error.message : '.');
                node.appendChild(message);
            }
        }

        function initialiseGiving() {
            var node = document.getElementById('adyen-giving');
            if (!node) {
                return;
            }

            fetch(contextPath + '/api/checkout/donations/context', {
                credentials: 'same-origin'
            })
                .then(function (response) {
                    if (response.status === 204) {
                        return null;
                    }
                    if (!response.ok) {
                        throw new Error('Giving context request failed');
                    }
                    return response.json();
                })
                .then(function (context) {
                    if (!context || !context.campaigns || !context.campaigns.length) {
                        return;
                    }
                    if (!window.AdyenWeb || !window.AdyenWeb.AdyenCheckout || !window.AdyenWeb.Donation) {
                        throw new Error('Adyen Web Giving component is not available');
                    }

                    return window.AdyenWeb.AdyenCheckout({
                        clientKey: context.clientKey,
                        environment: context.environment,
                        locale: context.locale,
                        countryCode: context.countryCode,
                        analytics: { enabled: false }
                    }).then(function (checkout) {
                        var campaign = context.campaigns[0];
                        var donation = new window.AdyenWeb.Donation(checkout, Object.assign({}, campaign, {
                            commercialTxAmount: context.commercialTxAmount,
                            onAmountSelected: function () {},
                            onCancel: function () {
                                node.innerHTML = '';
                            },
                            onDonate: function (state, component) {
                                component.setStatus('loading');
                                return fetch(contextPath + '/api/checkout/donations/donate', {
                                    method: 'POST',
                                    credentials: 'same-origin',
                                    headers: {
                                        'Content-Type': 'application/json',
                                        'CSRFToken': csrfToken
                                    },
                                    body: JSON.stringify({
                                        campaignId: campaign.id,
                                        amount: state.data.amount
                                    })
                                })
                                    .then(function (response) {
                                        if (!response.ok) {
                                            throw new Error('Giving donation request failed');
                                        }
                                        return response.json();
                                    })
                                    .then(function (result) {
                                        component.setStatus(result.status === 'completed' ? 'success' : 'error');
                                    })
                                    .catch(function () {
                                        component.setStatus('error');
                                    });
                            }
                        }));
                        donation.mount(node);
                    });
                })
                .catch(function (error) {
                    window.console.error('Adyen Giving could not be initialised', error);
                    showGivingError(error);
                });
        }

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', initialiseGiving);
        } else {
            initialiseGiving();
        }
    }());
</script>

<div class="well well-tertiary well-single-headline">
    <div class="well-headline">
        <spring:theme code="checkout.multi.order.summary" />
    </div>
</div>
