<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ attribute name="pageType" required="true" type="java.lang.String"%>


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
        applePayMerchantIdentifier: '${applePayMerchantIdentifier}',
        applePayMerchantName: '${applePayMerchantName}',
        pageType: '${pageType}',
        productCode: '${product.code}'
    }

    var checkoutConfig = {
        shopperLocale: '${shopperLocale}',
        environment: '${environmentMode}',
        clientKey: '${clientKey}',
        session: {
            id: '${sessionData.id}',
            sessionData: '${sessionData.sessionData}'
        },
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
    <input type="hidden" id="resultData" name="resultData"/>
    <input type="hidden" id="isResultError" name="isResultError" value="false"/>
</form:form>
