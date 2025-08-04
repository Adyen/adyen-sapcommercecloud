<%@ page trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

datacollection
<c:if test="${adyenDataCollectionEnabled}">
    datacollectionenabled
    <script type="text/javascript"
            src="https://${checkoutShopperHost}/checkoutshopper/assets/js/datacollection/datacollection.js">
    </script>
</c:if>
