<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="adyen" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>

<adyen:expressCheckoutConfig pageType="cart"/>

<c:if test="${not empty cartData.rootGroups}">
<%--    <div class="col-xs-12 pull-right cart-actions--print">--%>
        <div class="cart__actions border adyen-cart-express">
            <div class="row">
                <div class="col-sm-4 col-md-3 pull-right adyen-cart-express-btn hidden">
                    <div class="adyen-google-pay-button">
                    </div>
                </div>
                <div class="col-sm-4 col-md-3 pull-right adyen-cart-express-btn hidden">
                    <div class="adyen-apple-pay-button">
                    </div>
                </div>
                <div class="col-sm-4 col-md-3 pull-right adyen-cart-express-btn hidden">
                    <div class="adyen-paypal-button">
                    </div>
                </div>
            </div>
        </div>
<%--    </div>--%>
</c:if>