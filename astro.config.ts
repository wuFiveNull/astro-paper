import {
  defineConfig,
  envField,
  fontProviders,
  svgoOptimizer,
} from "astro/config";
import tailwindcss from "@tailwindcss/vite";
import mdx from "@astrojs/mdx";
import node from "@astrojs/node";
import { unified } from "@astrojs/markdown-remark";
import {
  remarkPlugins,
  rehypePlugins,
  shikiConfig,
} from "./src/config/markdown";
import config from "./astro-paper.config";

export default defineConfig({
  output: "server",
  adapter: node({ mode: "standalone" }),
  site: config.site.url,
  integrations: [mdx()],
  i18n: {
    locales: ["en"],
    defaultLocale: "en",
    routing: {
      prefixDefaultLocale: false,
    },
  },
  markdown: {
    processor: unified({ remarkPlugins, rehypePlugins }),
    shikiConfig,
  },
  vite: {
    plugins: [tailwindcss()],
    server: {
      proxy: {
        "/api/v1": {
          target: process.env.API_BASE_URL ?? "http://127.0.0.1:8081",
          changeOrigin: false,
        },
      },
    },
  },
  fonts: [
    {
      name: "Google Sans Code",
      cssVariable: "--font-google-sans-code",
      provider: fontProviders.local(),
      options: {
        variants: [
          {
            src: [
              "@fontsource/google-sans-code/files/google-sans-code-latin-300-normal.woff",
            ],
            weight: 300,
            style: "normal",
          },
          {
            src: [
              "@fontsource/google-sans-code/files/google-sans-code-latin-400-normal.woff",
            ],
            weight: 400,
            style: "normal",
          },
          {
            src: [
              "@fontsource/google-sans-code/files/google-sans-code-latin-500-normal.woff",
            ],
            weight: 500,
            style: "normal",
          },
          {
            src: [
              "@fontsource/google-sans-code/files/google-sans-code-latin-600-normal.woff",
            ],
            weight: 600,
            style: "normal",
          },
          {
            src: [
              "@fontsource/google-sans-code/files/google-sans-code-latin-700-normal.woff",
            ],
            weight: 700,
            style: "normal",
          },
        ],
      },
      fallbacks: ["monospace"],
      weights: [300, 400, 500, 600, 700],
      styles: ["normal", "italic"],
      subsets: ["latin"],
      formats: ["woff"],
    },
  ],
  env: {
    schema: {
      PUBLIC_GOOGLE_SITE_VERIFICATION: envField.string({
        access: "public",
        context: "client",
        optional: true,
      }),
    },
  },
  experimental: {
    svgOptimizer: svgoOptimizer(),
  },
});
