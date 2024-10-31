<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>
<%@ taglib prefix="json" uri="http://www.atg.com/taglibs/json" %>
<html>
<head>
    <adyen:adyenLibrary/>
    <c:set var="initConfig">
        <json:object escapeXml="false">
            <json:property name="locale" value="${shopperLocale}"/>
            <json:property name="environment" value="${environmentMode}"/>
            <json:property name="clientKey" value="${clientKey}"/>
            <json:property name="sessionId" value="${sessionData.id}"/>
            <json:property name="sessionData" value="${sessionData.sessionData}"/>
            <json:property name="countryCode" value="${countryCode}"/>
            <json:object name="risk" escapeXml="false">
                <json:property name="enabled" value="${false}"/>
            </json:object>
        </json:object>
    </c:set>
    <script type="text/javascript">

        const { AdyenCheckout, Dropin, Card, PayPal, GooglePay,
            ApplePay, CashAppPay, Sepa,Redirect,OnlineBankingIN,
            OnlineBankingPL, Ideal, EPS, Pix, WalletIN, AfterPay, Bcmc,
            Pos, PayBright, Boleto, SepaDirectDebit, RatePay, Paytm,Giftcard,Blik
        } = AdyenWeb;

        let checkout;
        const handleOnAdditionalDetails = (state) => {
            document.getElementById("details").value = JSON.stringify(state.data.details);
            document.getElementById("3ds2-form").submit();
        }

        const perform3DSOperations = async () => {
            const configuration = ${initConfig};
            configuration['onAdditionalDetails'] = this.handleOnAdditionalDetails
            checkout = await AdyenCheckout(configuration);
        }

        document.addEventListener('DOMContentLoaded', function () {
            perform3DSOperations().then(function () {
                const action = ${action};
                checkout.createFromAction(action).mount('#threeDS');
            });
        }, false);
    </script>
</head>
<body>
<div id="threeDS"></div>
<form method="post"
      class="create_update_payment_form"
      id="3ds2-form"
      action="authorise-3d-adyen-response"
>
    <input type="hidden" id="details" name="details"/>
</form>
</body>
</html>
