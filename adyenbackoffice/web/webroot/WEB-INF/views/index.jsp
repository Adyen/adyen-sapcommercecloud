<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <title>Adyen Backoffice Dashboard</title>
    <meta name="description" content="Adyen Backoffice Extension - Next.js Static Site integrated with SAP Commerce">
    <style>
      .no-border-iframe {
          border: none !important;
          box-shadow: none !important;
          outline: none !important;
          margin: 0 !important;
          padding: 0 !important;
          width: 100% !important;
          height: 100vh !important;
      }
    </style>
</head>
<body class="no-border-iframe">
  <!-- Embed the Next.js content using an iframe -->
  <iframe src="/adyenbackoffice/static/nextjs-app/out/index.html"
          frameborder="0"
          style="width:100%; height:100vh; border:none; padding: 0px; margin: 0px;"
          seamless
          class="no-border-iframe"></iframe>

</body>
</html>
