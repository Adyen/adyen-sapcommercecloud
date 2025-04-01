<%@ attribute name="supportedCountries" required="false" type="java.util.List" %>
<%@ attribute name="regions" required="false" type="java.util.List" %>
<%@ attribute name="country" required="false" type="java.lang.String" %>
<%@ attribute name="tabindex" required="false" type="java.lang.String" %>
<%@ taglib prefix="formElement" tagdir="/WEB-INF/tags/responsive/formElement" %>
<%@ taglib prefix="address" tagdir="/WEB-INF/tags/addons/adyenv6b2ccheckoutaddon/responsive" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<div id="billingAdyenCountrySelector" data-address-code="${fn:escapeXml(cartData.deliveryAddress.id)}" data-country-iso-code="${fn:escapeXml(cartData.deliveryAddress.country.isocode)}"
     data-display-title="false" class="clearfix">
    <formElement:formSelectBox idKey="address.country"
                               labelKey="address.country"
                               path="billingAddress.countryIso"
                               mandatory="true"
                               skipBlank="false"
                               skipBlankMessageKey="address.selectCountry"
                               items="${supportedCountries}"
                               itemValue="isocode"
                               tabindex="${tabindex}"
                               selectCSSClass="form-control"/>
</div>

<div id="adyenBillingAddressForm" class="billingAddressForm">
    <address:billingAddressFormElements regions="${regions}"
                                        country="${country}"
                                        tabindex="${tabindex + 1}"/>
</div>


