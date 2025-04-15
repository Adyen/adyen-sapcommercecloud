<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ attribute name="pageType" required="true" type="java.lang.String"%>

<spring:url value="/checkout/multi/adyen/summary/component-result-express" var="handleComponentResult"/>


<adyen:adyenLibrary
        dfUrl="${dfUrl}"
        showDefaultCss="${true}"
/>

<script type="text/javascript">
    var config = {
        amount: {
            value: '${amount.value}',
            currency: '${amount.currency}'
        },
        amountDecimal: '${amountDecimal}',
        merchantAccount: '${merchantAccount}',
        label: ['visible-xs', 'hidden-xs'],
        pageType: '${pageType}',
        productCode: '${product.code}',
        applePayMerchantName: '${applePayMerchantName}',
        applePayMerchantId: '${applePayMerchantIdentifier}',
        googlePayMerchantId: '${googlePayMerchantId}',
        googlePayGatewayMerchantId: '${googlePayGatewayMerchantId}',
        payPalIntent: '${paypalIntent}',
        payPalExpressEnabledOnProduct: ${expressPaymentConfig.paypalExpressEnabledOnProduct},
        payPalExpressEnabledOnCart: ${expressPaymentConfig.paypalExpressEnabledOnCart},
        googlePayExpressEnabledOnCart: ${expressPaymentConfig.googlePayExpressEnabledOnCart},
        applePayExpressEnabledOnCart: ${expressPaymentConfig.applePayExpressEnabledOnCart},
        googlePayExpressEnabledOnProduct: ${expressPaymentConfig.googlePayExpressEnabledOnProduct},
        applePayExpressEnabledOnProduct: ${expressPaymentConfig.applePayExpressEnabledOnProduct},
    }

    var checkoutConfig = {
        environment: '${environmentMode}',
        clientKey: '${clientKey}',
        countryCode: 'US'
    }

    window.onload = function() {
        AdyenExpressCheckoutHybris.initExpressCheckout(config, checkoutConfig);
    }
</script>

<form:form id="handleComponentResultForm"
           class="create_update_payment_form"
           action="${handleComponentResult}"
           method="post">
    <input type="hidden" id="resultCode" name="resultCode"/>
    <input type="hidden" id="merchantReference" name="merchantReference"/>
    <input type="hidden" id="isResultError" name="isResultError" value="false"/>
</form:form>
