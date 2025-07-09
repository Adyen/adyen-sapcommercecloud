<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Welcome to adyenbackoffice</title>
    <link rel="stylesheet" href="<c:url value="/static/adyenbackoffice-webapp.css"/>" type="text/css"
          media="screen, projection"/>
</head>
<div class="container">
    <img src="<c:url value="${logoUrl}" />" alt="Hybris platform logo"/>

    <h2>Welcome to "adyenbackoffice" extension</h2>

    <h3><strong>Getting started</strong></h3>

    <div>
        <p>
            This extension was generated using yEmpty template. It contains basic Spring MVC with sample Controller as
            well as WebAppMediaFilter enabled. Now you should go through the crucial configuration files and adjust them
            to your needs. Feel free to remove default controller and jsp pages.
        </p>

        <ul>
            <li><strong>resources/adyenbackoffice-items.xml</strong> - here you can model your items</li>
            <li><strong>resources/adyenbackoffice-spring.xml</strong> - here you can define your services</li>
            <li><strong>web/webroot/WEB-INF/config/adyenbackoffice-spring-mvc-config.xml</strong> - here is a Spring MVC related
                configuration
            </li>
            <li><strong>web/webroot/WEB-INF/config/adyenbackoffice-web-app-config.xml</strong> - here you can define web related services,
                facades etc.
            </li>
            <li><strong>web/webroot/WEB-INF/config/adyenbackoffice-spring-security-config.xml</strong> - here you can configure your Spring Security settings</li>
            <li><strong>web/webroot/WEB-INF/web.xml</strong> - here you can configure filters, servlets etc.</li>
            <li><strong>web/webroot/WEB-INF/views</strong> - here you can keep your jsp pages</li>
            <li><strong>web/webroot/static</strong> - here you can keep your static files, javascripts, css etc.</li>
        </ul>

        <p>
            This extension comes with basic Spring Security configuration which is disabled by default. If you want to
            enable it go to the <em>web/webroot/WEB-INF/web.xml</em> file and uncomment filter named <strong>springSecurityFilterChain</strong>,
            its filter mapping and restart application.
        </p>
    </div>
</div>
</html>
