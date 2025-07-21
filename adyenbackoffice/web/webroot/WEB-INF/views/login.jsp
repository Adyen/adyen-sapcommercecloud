<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Adyen Backoffice - Login</title>
    <meta name="description" content="Adyen Backoffice Login - SAP Commerce Integration">
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background-color: #f9fafb;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        .login-container {
            max-width: 400px;
            width: 100%;
            padding: 2.5rem;
            background: white;
            border-radius: 0.75rem;
            box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
            margin: 2rem;
        }
        
        .logo-container {
            text-align: center;
            margin-bottom: 2rem;
        }
        
        .logo {
            width: 120px;
            height: 40px;
            margin-bottom: 1.5rem;
        }
        
        .title {
            font-size: 1.875rem;
            font-weight: 800;
            color: #111827;
            margin-bottom: 0.5rem;
        }
        
        .subtitle {
            font-size: 0.875rem;
            color: #6b7280;
        }
        
        .error-message {
            background-color: #fef2f2;
            border-left: 4px solid #ef4444;
            padding: 1rem;
            margin-bottom: 1rem;
            border-radius: 0.375rem;
        }
        
        .error-text {
            font-size: 0.875rem;
            color: #dc2626;
        }
        
        .success-message {
            background-color: #f0fdf4;
            border-left: 4px solid #22c55e;
            padding: 1rem;
            margin-bottom: 1rem;
            border-radius: 0.375rem;
        }
        
        .success-text {
            font-size: 0.875rem;
            color: #16a34a;
        }
        
        .form-group {
            margin-bottom: 1.5rem;
        }
        
        .form-label {
            display: block;
            font-size: 0.875rem;
            font-weight: 500;
            color: #374151;
            margin-bottom: 0.5rem;
        }
        
        .form-input {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid #d1d5db;
            border-radius: 0.375rem;
            font-size: 0.875rem;
            transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
        }
        
        .form-input:focus {
            outline: none;
            border-color: #3b82f6;
            box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
        }
        
        .form-input:first-child {
            border-bottom-left-radius: 0;
            border-bottom-right-radius: 0;
            border-bottom: none;
        }
        
        .form-input:last-child {
            border-top-left-radius: 0;
            border-top-right-radius: 0;
        }
        
        .login-button {
            width: 100%;
            background-color: #3b82f6;
            color: white;
            padding: 0.75rem 1rem;
            border: none;
            border-radius: 0.375rem;
            font-size: 0.875rem;
            font-weight: 500;
            cursor: pointer;
            transition: background-color 0.15s ease-in-out;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        .login-button:hover {
            background-color: #2563eb;
        }
        
        .login-button:disabled {
            background-color: #93c5fd;
            cursor: not-allowed;
        }
        
        .login-icon {
            width: 1.25rem;
            height: 1.25rem;
            margin-right: 0.5rem;
        }
        
        .loading-spinner {
            width: 1.25rem;
            height: 1.25rem;
            border: 2px solid transparent;
            border-top: 2px solid white;
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin-right: 0.5rem;
        }
        
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        
        .input-group {
            position: relative;
            box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
            border-radius: 0.375rem;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="logo-container">
            <svg class="logo" viewBox="0 0 120 40" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect width="120" height="40" fill="#0ABF53"/>
                <text x="60" y="25" text-anchor="middle" fill="white" font-family="Arial, sans-serif" font-size="16" font-weight="bold">Adyen</text>
            </svg>
            <h2 class="title">Adyen Backoffice</h2>
            <p class="subtitle">Sign in with your SAP Commerce credentials</p>
        </div>
        
        <c:if test="${param.login_error == '1'}">
            <div class="error-message">
                <div class="error-text">
                    Invalid username or password. Please try again.
                </div>
            </div>
        </c:if>
        
        <c:if test="${param.logout == '1'}">
            <div class="success-message">
                <div class="success-text">
                    You have been successfully logged out.
                </div>
            </div>
        </c:if>
        
        <form action="<c:url value='/j_spring_security_check'/>" method="post" id="loginForm">
            <div class="form-group">
                <div class="input-group">
                    <input
                        id="j_username"
                        name="j_username"
                        type="text"
                        class="form-input"
                        placeholder="Username"
                        required
                        autocomplete="username"
                    />
                    <input
                        id="j_password"
                        name="j_password"
                        type="password"
                        class="form-input"
                        placeholder="Password"
                        required
                        autocomplete="current-password"
                    />
                </div>
            </div>
            
            <button type="submit" class="login-button" id="loginButton">
                <svg class="login-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1"/>
                </svg>
                Sign in
            </button>
        </form>
    </div>
    
    <script>
        document.getElementById('loginForm').addEventListener('submit', function() {
            const button = document.getElementById('loginButton');
            button.disabled = true;
            button.innerHTML = '<div class="loading-spinner"></div>Signing in...';
        });
    </script>
</body>
</html>
