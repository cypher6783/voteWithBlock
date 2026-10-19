import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/** @type {import('next').NextConfig} */
const nextConfig = {
  turbopack: {
    root: path.resolve(__dirname),
  },
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.INTERNAL_GATEWAY_URL || process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://core-gateway:8080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
