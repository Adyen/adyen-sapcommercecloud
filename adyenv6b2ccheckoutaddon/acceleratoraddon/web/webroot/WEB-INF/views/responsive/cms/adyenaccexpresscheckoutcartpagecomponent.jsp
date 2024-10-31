<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>

<adyen:expressCheckoutConfig pageType="cart"/>

<div class="cart__actions border">
    <div class="row">
        <div class="col-sm-4 col-md-3 pull-right">
            <div class="adyen-google-pay-button">
            </div>
        </div>
    </div>
</div>