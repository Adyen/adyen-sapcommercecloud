import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'export',
  trailingSlash: true,
  images: {
    unoptimized: true
  },
  basePath: '/adyenbackoffice/static/nextjs-app/out',
  assetPrefix: '/adyenbackoffice/static/nextjs-app/out',
  distDir: 'out'
};

export default nextConfig;
