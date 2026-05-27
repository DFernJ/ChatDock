# ─────────────── Stage 1: build con pnpm ───────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Activa pnpm vía corepack (usa la versión declarada en package.json -> packageManager)
RUN corepack enable

# Instala dependencias primero (mejor cache de capas)
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile || pnpm install

# Copia el resto y compila
COPY . .
RUN pnpm build

# ─────────────── Stage 2: servir con nginx ───────────────
FROM nginx:alpine AS runtime

# Config propia del contenedor frontend
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Artefactos compilados por Vite
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
  CMD wget -qO- http://localhost/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
