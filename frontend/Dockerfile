FROM node:22.23.3-alpine3.24@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS builder
WORKDIR /app
COPY package*.json ./
COPY scripts ./scripts
RUN npm ci --legacy-peer-deps
COPY . .

ARG PUBLIC_API_URL
ENV PUBLIC_API_URL=$PUBLIC_API_URL
ARG SSR_API_URL
ENV SSR_API_URL=$SSR_API_URL
ARG PUBLIC_STATUS_URL
ENV PUBLIC_STATUS_URL=$PUBLIC_STATUS_URL

RUN npm run check && npm test && npm run build && npm run test:html-cache && npm prune --omit=dev --legacy-peer-deps

FROM node:22.23.3-alpine3.24@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/scripts ./scripts

ENV HOST=0.0.0.0
ENV PORT=8080

EXPOSE 8080

CMD ["node", "./scripts/start-server.mjs"]
