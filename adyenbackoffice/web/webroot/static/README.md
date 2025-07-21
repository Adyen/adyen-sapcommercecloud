# Next.js Static Site in SAP Commerce Adyen Backoffice Extension

This directory contains a Next.js project configured for Static Site Generation (SSG) that integrates with the SAP Commerce Adyen Backoffice extension.

## Project Structure

```
static/
├── nextjs-app/                 # Next.js project directory
│   ├── src/app/               # Next.js app directory
│   ├── next.config.ts         # Next.js configuration for static export
│   ├── package.json           # Node.js dependencies
│   └── out/                   # Generated static files (after build)
├── index.jsp                  # JSP file that serves the Next.js static content
├── build-nextjs.sh           # Build script for automation
├── adyenbackoffice-webapp.css # Existing Adyen backoffice styles
└── README.md                 # This file
```

## Configuration

The Next.js project is configured for static export with the following settings in `next.config.ts`:

- `output: 'export'` - Enables static site generation
- `trailingSlash: true` - Adds trailing slashes to URLs
- `images: { unoptimized: true }` - Disables image optimization for static export
- `basePath: '/static/nextjs-app'` - Sets the base path for assets
- `assetPrefix: '/static/nextjs-app'` - Prefixes all assets with this path
- `distDir: 'out'` - Output directory for static files

## Building the Project

### Manual Build

1. Navigate to the Next.js project directory:
   ```bash
   cd bin/custom/adyen-hybris/adyenbackoffice/web/webroot/static/nextjs-app
   ```

2. Install dependencies (if not already installed):
   ```bash
   npm install
   ```

3. Build the static site:
   ```bash
   npm run build
   ```

### Automated Build

Use the provided build script:

```bash
cd bin/custom/adyen-hybris/adyenbackoffice/web/webroot/static
./build-nextjs.sh
```

## Deployment

1. Build the Next.js project using one of the methods above
2. Deploy your SAP Commerce extension as usual
3. The static Next.js application will be available at: `/static/index.jsp`

## Development

### Adding New Pages

To add new pages to the Next.js application:

1. Create new page components in `nextjs-app/src/app/`
2. Update the routing as needed
3. Rebuild the project
4. Update the `index.jsp` file if necessary to include new static assets

### Styling

The project uses Tailwind CSS for styling. You can:

- Modify existing styles in the React components
- Add custom CSS classes using Tailwind utilities
- Extend the Tailwind configuration if needed

### Integration with SAP Commerce

The `index.jsp` file serves as the bridge between SAP Commerce and the Next.js static content:

- Uses JSP taglibs for URL generation (`<c:url>`)
- Includes both Next.js assets and existing Adyen backoffice styles
- Maintains compatibility with SAP Commerce's web application structure

## Features

The current implementation includes:

- **Payment Management** - Dashboard for payment configurations
- **Configuration** - Adyen settings and API credentials
- **Analytics** - Payment analytics and reporting
- **Webhooks** - Webhook management and logs
- **Error Logs** - Payment error monitoring
- **Documentation** - Integration guides and API docs

## Customization

To customize the application:

1. Edit the React components in `nextjs-app/src/app/`
2. Modify styles using Tailwind CSS classes
3. Add new functionality as needed
4. Rebuild and redeploy

## Troubleshooting

### Build Issues

- Ensure Node.js and npm are installed
- Check that all dependencies are installed (`npm install`)
- Verify the Next.js configuration is correct

### Asset Loading Issues

- Check that the asset paths in `index.jsp` match the generated files in `nextjs-app/out/`
- Ensure the SAP Commerce server can serve static files from the `/static/` directory
- Verify that the `basePath` and `assetPrefix` in `next.config.ts` are correct

### JSP Integration Issues

- Ensure JSP taglibs are properly configured
- Check that the SAP Commerce web application context is set up correctly
- Verify that the static directory is accessible from the web application