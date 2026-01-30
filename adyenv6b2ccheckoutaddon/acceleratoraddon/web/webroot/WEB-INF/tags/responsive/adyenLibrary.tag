<%--
  ~                        ######
  ~                        ######
  ~  ############    ####( ######  #####. ######  ############   ############
  ~  #############  #####( ######  #####. ######  #############  #############
  ~         ######  #####( ######  #####. ######  #####  ######  #####  ######
  ~  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
  ~  ###### ######  #####( ######  #####. ######  #####          #####  ######
  ~  #############  #############  #############  #############  #####  ######
  ~   ############   ############  #############   ############  #####  ######
  ~                                       ######
  ~                                #############
  ~                                ############
  ~
  ~  Adyen Hybris Extension
  ~
  ~  Copyright (c) 2017 Adyen B.V.
  ~  This file is open source and available under the MIT license.
  ~  See the LICENSE file for more info.
  --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ attribute name="dfUrl" required="false" type="java.lang.String"%>
<%@ attribute name="showDefaultCss" required="false" type="java.lang.Boolean"%>

<c:set var="VERSION" value="6.25.1"/>
<c:set var="jsHashVersion" value="sha384-baZefgr0+MsmvaL/UdoKcOym2+Snxf0GBCwopMGahl/M9hHul1ty2f3fIb2SG2/5"/>
<c:set var="cssHashVersion" value="sha384-TqsUArVcaNhMj9GdL9EJx9QTEcuL6ozA/hqIDJqoj/qDT/FZqjnUWbvMYPVuebMl"/>

<c:if test="${not empty(dfUrl)}">
    <script type="text/javascript" src="${dfUrl}"></script>
</c:if>

<script src="https://${checkoutShopperHost}/checkoutshopper/sdk/${VERSION}/adyen.js"
        integrity="${hashVersion}"
        crossorigin="anonymous">
</script>

<c:if test="${showDefaultCss eq true}">
    <link rel="stylesheet" href="https://${checkoutShopperHost}/checkoutshopper/css/chckt-default-v1.css"/>
</c:if>

<link rel="stylesheet"
      href="https://${checkoutShopperHost}/checkoutshopper/sdk/${VERSION}/adyen.css"
      integrity="${cssHashVersion}"
      crossorigin="anonymous"/>
