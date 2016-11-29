<%@ page import="java.util.List" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<html>
<head>
    <style type="text/css">
        body {
            font-family: sans-serif;
        }
    </style>
</head>
<body>
<h1>Greetings:</h1>
<p>
    These are the messages of the currently installed CustomAppPlugins:
</p>
<ul>
    <c:forEach items="${greetings}" var="greeting">
        <li>
            ${greeting}
        </li>
    </c:forEach>
</ul>


<h2>Reststop plugins:</h2>
<ul>
    <c:forEach items="${reststopPlugins}" var="plugin">
        <li>${plugin}</li>
    </c:forEach>
</ul>
</body>
</html>