# Build the Astro server-rendered site.
FROM node:lts-alpine AS base
WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@10.12.1 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm run build
RUN pnpm prune --prod

# Runtime stage for the Astro standalone Node server.
FROM node:lts-alpine AS runtime
WORKDIR /app
ENV HOST=0.0.0.0
ENV PORT=4321
COPY --from=base /app/dist ./dist
COPY --from=base /app/node_modules ./node_modules
EXPOSE 4321
CMD ["node", "./dist/server/entry.mjs"]
