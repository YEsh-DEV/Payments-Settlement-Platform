# syntax=docker/dockerfile:1

# ── Build stage: compile the Vite/React app to static assets ──────────────────
FROM node:22-alpine AS build
WORKDIR /app

# Install deps first for better layer caching. Fall back to `npm install` if the
# lockfile is out of sync so the image still builds.
COPY package.json package-lock.json ./
RUN npm ci || npm install

COPY . .
RUN npm run build

# ── Serve stage: static files behind nginx, with /api proxied to the backend ──
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
