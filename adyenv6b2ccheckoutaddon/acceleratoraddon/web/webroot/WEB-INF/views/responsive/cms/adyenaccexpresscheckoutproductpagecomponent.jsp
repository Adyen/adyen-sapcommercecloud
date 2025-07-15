<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>

<adyen:expressCheckoutConfig pageType="PDP"/>
<div class="adyen-pdp-express">
    <div class="adyen-google-pay-button adyen-pdp-express-btn">
    </div>
    <div class="adyen-apple-pay-button adyen-pdp-express-btn">
    </div>
    <div class="adyen-paypal-button adyen-pdp-express-btn">
    </div>
</div>