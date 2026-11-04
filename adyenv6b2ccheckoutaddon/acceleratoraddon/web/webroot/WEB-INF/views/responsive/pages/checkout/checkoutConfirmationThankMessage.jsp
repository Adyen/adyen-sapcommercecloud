<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="formElement" tagdir="/WEB-INF/tags/responsive/formElement" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ycommerce" uri="http://hybris.com/tld/ycommercetags" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="order" tagdir="/WEB-INF/tags/responsive/order" %>
<%@ taglib prefix="template" tagdir="/WEB-INF/tags/responsive/template"%>

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

<%-- keep in sync with adyenLibrary.tag --%>
<c:set var="adyenGivingSdkVersion" value="6.40.1"/>
<c:set var="adyenGivingSdkJsIntegrity" value="sha384-DSWsEqnnD+jomUFfNHEGA302VKHKB/aMOWJm312FYhNVcFutrbEgrLHuU3vLwwBe"/>
<c:set var="adyenGivingSdkCssIntegrity" value="sha384-QCE3u3lliHH3SEx30M8NEuDhvEGmFCtZl62YVSVWw84cYNt2NYYbVwd/KPaaaIQ9"/>
<script type="text/javascript">
    (function () {
        var contextPath = '${fn:escapeXml(encodedContextPath)}';
        var csrfToken = '${ycommerce:encodeJavaScript(CSRFToken.token)}';
        var sdkVersion = '${ycommerce:encodeJavaScript(adyenGivingSdkVersion)}';
        var sdkJsIntegrity = '${ycommerce:encodeJavaScript(adyenGivingSdkJsIntegrity)}';
        var sdkCssIntegrity = '${ycommerce:encodeJavaScript(adyenGivingSdkCssIntegrity)}';

        function hideGiving(error) {
            var node = document.getElementById('adyen-giving');
            if (node) {
                node.innerHTML = '';
            }
            window.console.warn('Adyen Giving is not available', error);
        }

        function loadGivingSdk(host) {
            if (window.AdyenWeb && window.AdyenWeb.Donation) {
                return Promise.resolve();
            }
            if (!host) {
                return Promise.reject(new Error('Adyen checkout shopper host is missing'));
            }
            var baseUrl = 'https://' + host + '/checkoutshopper/sdk/' + sdkVersion + '/adyen';
            return new Promise(function (resolve, reject) {
                var link = document.createElement('link');
                link.rel = 'stylesheet';
                link.href = baseUrl + '.css';
                link.integrity = sdkCssIntegrity;
                link.crossOrigin = 'anonymous';
                document.head.appendChild(link);

                var script = document.createElement('script');
                script.src = baseUrl + '.js';
                script.integrity = sdkJsIntegrity;
                script.crossOrigin = 'anonymous';
                script.onload = function () { resolve(); };
                script.onerror = function () { reject(new Error('Adyen Giving SDK could not be loaded')); };
                document.head.appendChild(script);
            });
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
                    return loadGivingSdk(context.checkoutShopperHost).then(function () {
                        if (!window.AdyenWeb || !window.AdyenWeb.AdyenCheckout || !window.AdyenWeb.Donation) {
                            throw new Error('Adyen Web Giving component is not available');
                        }
                        return window.AdyenWeb.AdyenCheckout({
                            clientKey: context.clientKey,
                            environment: context.environment,
                            locale: context.locale,
                            countryCode: context.countryCode,
                            analytics: { enabled: false }
                        });
                    }).then(function (checkout) {
                        var campaign = context.campaigns[0];
                        var donation = new window.AdyenWeb.Donation(checkout, Object.assign({}, campaign, {
                            commercialTxAmount: context.commercialTxAmount,
                            onAmountSelected: function () {},
                            onCancel: function () {
                                donation.unmount();
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
                                        donationCampaignId: campaign.id,
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
                    hideGiving(error);
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
