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

<c:set var="VERSION" value="6.40.1"/>
<c:set var="jsHashVersion" value="sha384-DSWsEqnnD+jomUFfNHEGA302VKHKB/aMOWJm312FYhNVcFutrbEgrLHuU3vLwwBe"/>
<c:set var="cssHashVersion" value="sha384-QCE3u3lliHH3SEx30M8NEuDhvEGmFCtZl62YVSVWw84cYNt2NYYbVwd/KPaaaIQ9"/>

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
