FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY . .

ARG PUBLIC_API_URL
ENV PUBLIC_API_URL=$PUBLIC_API_URL
# ------------------------

RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/node_modules ./node_modules

ENV HOST=0.0.0.0
ENV PORT=8080
ENV PUBLIC_API_URL=https://api.modtale.net/api

EXPOSE 8080

CMD ["node", "./dist/server/entry.mjs"]