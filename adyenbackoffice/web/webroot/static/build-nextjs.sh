#!/bin/bash

# Build script for Next.js static site generation in SAP Commerce Adyen Backoffice extension

echo "Building Next.js static site for Adyen Backoffice..."

# Navigate to the Next.js project directory
cd "$(dirname "$0")/nextjs-app"

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi

# Build the Next.js project for static export
echo "Building Next.js project..."
npm run build

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "✅ Next.js build completed successfully!"
    echo "📁 Static files are available in: nextjs-app/out/"
    echo "🌐 JSP file created at: index.jsp"
    echo ""
    echo "To serve the application:"
    echo "1. Deploy your SAP Commerce extension"
    echo "2. Access the application at: /static/index.jsp"
    echo ""
    echo "Static assets are served from: /static/nextjs-app/out/"
else
    echo "❌ Next.js build failed!"
    exit 1
fi